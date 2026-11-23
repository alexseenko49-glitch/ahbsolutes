#!/usr/bin/env bash
# Builds AHbsolute with automatic fallback so CI stays green whenever a build is still possible:
#   1) full features + arm64/armv7 only
#   2) full features, all ABIs   (in case the ABI filter is the problem)
#   3) SAFE mode: only name / api_id / package / icon (UI features dropped)
set -uo pipefail
ROOT="$(pwd)"; UP="$ROOT/upstream"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

keystore() {
  if [ -n "${KS:-}" ]; then
    mkdir -p "$UP/TMessagesProj/config"
    echo "$KS" | base64 -d > "$UP/TMessagesProj/config/release.keystore" || return 1
    { echo "RELEASE_KEY_PASSWORD=${KP:-}"; echo "RELEASE_KEY_ALIAS=${KA:-}"; echo "RELEASE_STORE_PASSWORD=${KP:-}"; } >> "$UP/gradle.properties"
  else
    echo "::warning::No keystore secret: APK is signed with upstream's dummy key (fine for personal use, not for publishing)."
  fi
}

prepare() {
  local mode="$1"
  echo "=== prepare ($mode) ==="
  if [ "$mode" = safe ]; then
    git -C "$UP" checkout -- . && git -C "$UP" clean -fdq || return 1
    python3 scripts/apply_patches.py "$UP" --safe || return 1
  else
    cp -r overlay/. "$UP/" || return 1
    python3 scripts/apply_patches.py "$UP" || return 1
  fi
  python3 scripts/make_icons.py "$UP" || return 1
  keystore || return 1
}

build() {
  local abi="$1"
  echo "=== gradle ($abi) ==="
  ( cd "$UP" && chmod +x gradlew && ./gradlew :TMessagesProj_App:assembleAfatRelease --no-daemon --stacktrace $abi ) 2>&1 | tee -a "$ROOT/build.log"
  return "${PIPESTATUS[0]}"
}

MODE=""
if prepare full; then
  if build "-Pandroid.injected.build.abi=arm64-v8a,armeabi-v7a"; then MODE=full
  elif build ""; then MODE=full-allabi
  fi
fi

if [ -z "$MODE" ]; then
  echo "::warning::Full AHbsolute build failed. Falling back to SAFE mode (no menu editor / avatar shapes)."
  if prepare safe && build ""; then MODE=safe; fi
fi

if [ -z "$MODE" ]; then
  echo "::error::All build attempts failed. Last 60 log lines are above; send them to get a fix."
  exit 1
fi

echo "MODE=$MODE" >> "$GITHUB_ENV"
{
  echo "### AHbsolute build mode: \`$MODE\`"
  if [ "$MODE" = safe ]; then
    echo "UI features (interface editor, avatar shapes) were **left out** because the full build failed. Name, own api_id and icon are applied. Send the first error from the log of the first attempt to get the features working."
  else
    echo "All AHbsolute features are included."
  fi
} >> "$SUMMARY"
