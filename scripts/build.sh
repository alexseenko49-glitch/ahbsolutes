#!/usr/bin/env bash
# AHbsolute build with automatic fallback (written without || and && so it survives copy-paste):
#   1) full features, arm64 + armv7 only
#   2) full features, all ABIs
#   3) SAFE mode: only name / api_id / package / icon
set -uo pipefail
ROOT="$(pwd)"
UP="$ROOT/upstream"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
: > "$ROOT/build.log"

for f in scripts/apply_patches.py scripts/make_icons.py assets/icon.png overlay/TMessagesProj/src/main/java/org/telegram/messenger/ahbsolute/AHbsolute.java; do
  if [ ! -f "$f" ]; then
    echo "::error::Missing file $f. Upload the folders scripts/, overlay/ and assets/ from the zip (hidden or empty folders are skipped by drag and drop)."
    exit 1
  fi
done
if [ ! -f "$UP/gradlew" ]; then
  echo "::error::upstream/gradlew not found - Telegram-Android was not checked out."
  exit 1
fi

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
  echo "=== gradle (${abi:-all ABIs}) ==="
  cd "$UP"
  chmod +x gradlew
  local init=""
  if [ -f "$UP/.ahb_force_pkg" ]; then
    init="-I $ROOT/scripts/ahb-init.gradle"
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

MODE=""
if prepare full ; then
  if build "-Pandroid.injected.build.abi=arm64-v8a,armeabi-v7a" ; then
    MODE=full
  else
    if build "" ; then
      MODE=full-allabi
    fi
  fi
fi

if [ -z "$MODE" ]; then
  echo "::warning::Full AHbsolute build failed. Falling back to SAFE mode (no menu editor / avatar shapes)."
  if prepare safe ; then
    if build "" ; then
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
    echo "All AHbsolute features are included."
  fi
} >> "$SUMMARY"
