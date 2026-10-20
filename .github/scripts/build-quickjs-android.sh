#!/usr/bin/env bash
# Build QuickJS-NG CLI executables for Android and package them as native libs.
# Based on the upstream build system (MIT license), not copied from other apps.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  NDK="${SDK}/ndk/27.2.12479018"
fi
if [[ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK 27.2.12479018 not found. Set ANDROID_NDK_HOME." >&2
  exit 1
fi

# QuickJS-NG v0.17.0, released 2026-09-18.
QJS_REV="6d46d07d04041b40f4f49eaa7fdebe44c314c699"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git clone --quiet --depth 1 --branch v0.17.0 https://github.com/quickjs-ng/quickjs.git "$WORK/quickjs"
CURRENT="$(git -C "$WORK/quickjs" rev-parse HEAD)"
if [[ "$CURRENT" != "$QJS_REV" ]]; then
  echo "Unexpected QuickJS-NG source revision: $CURRENT" >&2
  exit 1
fi

for abi in arm64-v8a armeabi-v7a x86_64; do
  echo "Building QuickJS-NG $QJS_REV for $abi"
  out="$WORK/build-$abi"
  cmake -S "$WORK/quickjs" -B "$out" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DQJS_ENABLE_INSTALL=OFF \
    -DQJS_BUILD_EXAMPLES=OFF
  cmake --build "$out" --target qjs_exe --parallel 4
  test -s "$out/qjs"
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  install -m 755 "$out/qjs" "$ROOT/app/src/main/jniLibs/$abi/libqjs.so"
  file "$ROOT/app/src/main/jniLibs/$abi/libqjs.so"
done

echo "Bundled QuickJS-NG v0.17.0 for three supported Android ABIs."
