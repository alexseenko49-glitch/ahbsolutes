#!/usr/bin/env bash
# AHbsolute build with automatic fallback (written without || and && so it survives copy-paste).
# exit code 143 on GitHub runners = process killed for lack of memory (R8), so by default:
#   - swap file + smaller Gradle heap
#   - R8/minify is OFF (bigger APK, same features). Set env AH_MINIFY=1 to try R8 first.
# Attempts (a SAFE build is never produced; every attempt keeps the menu editor and/or avatar shapes):
#   all(menu,avatar,settings,title,streak) -> without streak -> without title -> menu+avatar+title -> menu+avatar -> avatar+settings -> menu -> avatar
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

runlog() {
  "$@" 2>&1 | tee -a "$ROOT/build.log"
  return "${PIPESTATUS[0]}"
}

diagnostics() {
  {
    echo "=== DIAGNOSTICS: upstream files the patches depend on ==="
    local f
    f=$(find "$UP" -name DrawerLayoutAdapter.java -not -path '*/build/*' | head -1)
    echo "--- $f"
    if [ -n "$f" ]; then cat "$f"; fi
    f=$(find "$UP" -name LaunchActivity.java -not -path '*/build/*' | head -1)
    echo "--- $f (lines around drawerLayoutAdapter / click listeners)"
    if [ -n "$f" ]; then grep -n -B2 -A4 "drawerLayoutAdapter.getId\|setOnItemClickListener" "$f" | head -120; fi
    f=$(find "$UP" -name ImageReceiver.java -not -path '*/build/*' | head -1)
    echo "--- $f (draw / setForUserOrChat / roundRadius)"
    if [ -n "$f" ]; then grep -n "boolean draw(\|setForUserOrChat\|int\[\] roundRadius\|float getImageX\|float getImageWidth" "$f" | head -60; fi
    echo "=== END DIAGNOSTICS ==="
  } >> "$ROOT/build.log" 2>&1
}

free_disk() {
  echo "=== freeing disk space ==="
  df -h / /mnt
  sudo rm -rf /usr/share/dotnet /usr/local/.ghcup /usr/share/swift /usr/local/share/powershell /usr/local/share/chromium /usr/local/share/edge_driver /usr/local/julia* /opt/az /opt/microsoft /opt/ghc /usr/lib/google-cloud-sdk /usr/lib/mono /usr/local/lib/node_modules /usr/local/share/boost /opt/hostedtoolcache/CodeQL /opt/hostedtoolcache/go /opt/hostedtoolcache/Ruby /opt/hostedtoolcache/PyPy /opt/hostedtoolcache/node
  sudo docker image prune --all --force > /dev/null 2>&1
  sudo apt-get clean
  df -h / /mnt
}

tune_memory() {
  echo "=== memory tuning ==="
  free -h
  sudo swapoff -a
  sudo fallocate -l 6G /mnt/ahb-swap
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
  local feats="$1"
  echo "=== prepare ($feats) ===" | tee -a "$ROOT/build.log"
  if ! git -C "$UP" checkout -- . ; then return 1; fi
  git -C "$UP" clean -fdq
  if ! cp -r overlay/. "$UP/" ; then return 1; fi
  if ! runlog python3 scripts/apply_patches.py "$UP" "--features=$feats" ; then return 1; fi
  if ! runlog python3 scripts/make_icons.py "$UP" ; then return 1; fi
  if ! keystore ; then return 1; fi
  return 0
}

collect_apks() {
  local out="$UP/dist/build/outputs/apk/ahbsolute"
  rm -rf "$UP/dist"
  mkdir -p "$out"
  local n=0
  local f
  while IFS= read -r f; do
    n=$((n + 1))
    mv "$f" "$out/$n-$(basename "$f")"
  done < <(find "$UP" -name '*.apk' -size +5M -not -path '*/intermediates/*' -not -path "$UP/dist/*" 2>/dev/null)
  if [ "$n" -eq 0 ]; then
    while IFS= read -r f; do
      n=$((n + 1))
      mv "$f" "$out/$n-$(basename "$f")"
    done < <(find "$UP" -name '*.apk' -size +5M -not -path "$UP/dist/*" 2>/dev/null)
  fi
  echo "APK files collected: $n"
  if [ "$n" -eq 0 ]; then
    echo "--- files under build/outputs (diagnostics):"
    find "$UP" -path '*build/outputs*' -type f 2>/dev/null | head -40
    return 1
  fi
  ls -la "$out"
  return 0
}

build() {
  local abi="$1"
  local nomin="$2"
  echo "=== gradle (abi: ${abi:-all}, minify off: $nomin) ==="
  df -h / | tail -1
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
  if [ "$rc" -eq 0 ]; then
    if ! collect_apks ; then
      echo "::warning::Gradle finished but no APK was found."
      rc=1
    fi
  fi
  if [ "$rc" -ne 0 ]; then
    echo "::warning::Gradle attempt failed (exit $rc). Last lines:"
    tail -n 40 "$ROOT/build.log"
  fi
  return "$rc"
}

ABI="-Pandroid.injected.build.abi=arm64-v8a,armeabi-v7a"
free_disk
tune_memory
diagnostics

MODE=""
TRIED="|"
for feats in "menu,avatar,settings,title,streak" "menu,avatar,settings,title" "menu,avatar,settings" "menu,avatar,title" "menu,avatar" "avatar,settings" "menu" "avatar" ; do
  if [ -n "$MODE" ]; then break; fi
  if [ "$SECONDS" -gt 18000 ]; then
    echo "::warning::Less than an hour left of the 6h runner limit; not starting another attempt."
    break
  fi
  if ! prepare "$feats" ; then
    echo "::warning::Could not apply patches for: $feats"
    continue
  fi
  APPLIED="$(cat "$UP/.ahb_features" 2>/dev/null)"
  case "$TRIED" in
    *"|$APPLIED|"*)
      echo "::notice::Feature set '$APPLIED' was already tried, skipping."
      continue
      ;;
  esac
  TRIED="$TRIED$APPLIED|"
  echo "=== building with features: $APPLIED ==="
  if [ "${AH_MINIFY:-0}" = "1" ]; then
    if build "$ABI" 0 ; then
      MODE="$APPLIED"
    fi
  fi
  if [ -z "$MODE" ]; then
    if build "$ABI" 1 ; then
      MODE="$APPLIED"
    fi
  fi
  if [ -z "$MODE" ]; then
    echo "::warning::Build with '$APPLIED' failed, trying a smaller feature set."
  fi
done

if [ -z "$MODE" ]; then
  echo "::error::All build attempts failed (no SAFE build is produced by design). Send the first 'error:' or 'FAIL:' line and the last 40 log lines."
  exit 1
fi

MODE_NAME="${MODE//,/-}"
echo "MODE=$MODE_NAME" >> "$GITHUB_ENV"
{
  echo "### AHbsolute build features: \`$MODE\`"
  echo "menu = interface editor, avatar = avatar shapes, settings = row 'Кастомизация Пользователя' in Settings. R8 minify was off to avoid running out of memory (APK is larger)."
} >> "$SUMMARY"
