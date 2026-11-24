#!/usr/bin/env bash
# AHbsolute build with automatic fallback (written without || and && so it survives copy-paste).
# exit code 143 on GitHub runners = process killed for lack of memory (R8), so by default:
#   - swap file + smaller Gradle heap
#   - R8/minify is OFF (bigger APK, same features). Set env AH_MINIFY=1 to try R8 first.
# Attempts:  1) full features   2) SAFE mode (name / api_id / package / icon only)
set -uo pipefail
ROOT="$(pwd)"
UP="$ROOT/upstream"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
: > "$ROOT/build.log"

for f in scripts/apply_patches.py scripts/make_icons.py scripts/ahb-init.gradle assets/icon.png overlay/TMessagesProj/src/main/java/org/telegram/messenger/ahbsolute/AHbsolute.java; do
  if [ ! -f "$f" ]; then
    echo "::error::Missing file $f. Upload the folders scripts/, overlay/ and assets/ from the zip."
    exit 1
  fi
done
if [ ! -f "$UP/gradlew" ]; then
  echo "::error::upstream/gradlew not found - Telegram-Android was not checked out."
  exit 1
fi

tune_memory() {
  echo "=== memory tuning ==="
  free -h
  sudo swapoff -a
  sudo fallocate -l 8G /mnt/ahb-swap
  sudo chmod 600 /mnt/ahb-swap
  sudo mkswap /mnt/ahb-swap > /dev/null
  sudo swapon /mnt/ahb-swap
  mkdir -p "$HOME/.gradle"
  {
    echo "org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"
    echo "org.gradle.workers.max=2"
    echo "org.gradle.parallel=false"
  } > "$HOME/.gradle/gradle.properties"
  free -h
}

keystore() {
  if [ -z "${KS:-}" ]; then
    echo "::warning::No keystore secret: APK is signed with upstream dummy key (fine for personal use)."
    return 0
  fi
  mkdir -p "$UP/TMessagesProj/config"
  if ! echo "$KS" | base64 -d > "$UP/TMessagesProj/config/release.keystore"; then
    return 1
  fi
  {
    echo "RELEASE_KEY_PASSWORD=${KP:-}"
    echo "RELEASE_KEY_ALIAS=${KA:-}"
    echo "RELEASE_STORE_PASSWORD=${KP:-}"
  } >> "$UP/gradle.properties"
  return 0
}

prepare() {
  local mode="$1"
  echo "=== prepare ($mode) ==="
  if [ "$mode" = safe ]; then
    if ! git -C "$UP" checkout -- . ; then return 1; fi
    git -C "$UP" clean -fdq
    if ! python3 scripts/apply_patches.py "$UP" --safe ; then return 1; fi
  else
    if ! cp -r overlay/. "$UP/" ; then return 1; fi
    if ! python3 scripts/apply_patches.py "$UP" ; then return 1; fi
  fi
  if ! python3 scripts/make_icons.py "$UP" ; then return 1; fi
  if ! keystore ; then return 1; fi
  return 0
}

build() {
  local abi="$1"
  local nomin="$2"
  echo "=== gradle (abi: ${abi:-all}, minify off: $nomin) ==="
  cd "$UP"
  chmod +x gradlew
  local init=""
  if [ -f "$UP/.ahb_force_pkg" ]; then
    init="-I $ROOT/scripts/ahb-init.gradle"
  fi
  if [ "$nomin" = "1" ]; then
    init="-I $ROOT/scripts/ahb-init.gradle"
    export AH_NO_MINIFY=1
  else
    export AH_NO_MINIFY=0
  fi
  ./gradlew :TMessagesProj_App:assembleAfatRelease --no-daemon --stacktrace $init $abi 2>&1 | tee -a "$ROOT/build.log"
  local rc=${PIPESTATUS[0]}
  cd "$ROOT"
  if [ "$rc" -ne 0 ]; then
    echo "::warning::Gradle attempt failed (exit $rc). Last lines:"
    tail -n 40 "$ROOT/build.log"
  fi
  return "$rc"
}

ABI="-Pandroid.injected.build.abi=arm64-v8a,armeabi-v7a"
tune_memory

MODE=""
if prepare full ; then
  if [ "${AH_MINIFY:-0}" = "1" ]; then
    if build "$ABI" 0 ; then
      MODE=full
    fi
  fi
  if [ -z "$MODE" ]; then
    if build "$ABI" 1 ; then
      MODE=full
    fi
  fi
fi

if [ -z "$MODE" ]; then
  echo "::warning::Full AHbsolute build failed. Falling back to SAFE mode (no menu editor / avatar shapes)."
  if prepare safe ; then
    if build "$ABI" 1 ; then
      MODE=safe
    fi
  fi
fi

if [ -z "$MODE" ]; then
  echo "::error::All build attempts failed. Send the first 'error:' or 'FAIL:' line and the last 40 log lines."
  exit 1
fi

echo "MODE=$MODE" >> "$GITHUB_ENV"
{
  echo "### AHbsolute build mode: \`$MODE\`"
  if [ "$MODE" = safe ]; then
    echo "UI features (interface editor, avatar shapes) were left out because the full build failed."
  else
    echo "All AHbsolute features are included. R8 minify was off to avoid running out of memory (APK is larger)."
  fi
} >> "$SUMMARY"
