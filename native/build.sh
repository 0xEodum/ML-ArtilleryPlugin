#!/usr/bin/env bash
#
# Builds the optional native solver.
#
#   ./native/build.sh          both targets, skipping any whose toolchain is absent
#   ./native/build.sh linux    libairtillery_ballistics.so
#   ./native/build.sh windows  airtillery_ballistics.dll  (needs mingw-w64)
#
# Output lands in native/build/, which pom.xml picks up as a resource. The
# plugin runs on the Java solver when the directory is empty, so this script is
# never required to produce a working build.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
src="$here/src/ballistics.cpp"
out="$here/build"
mkdir -p "$out"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
jni_include="-I$JAVA_HOME/include"

flags="-O3 -fPIC -shared -std=c++17 -fvisibility=hidden -ffp-contract=off"
# -ffp-contract=off keeps the C++ arithmetic bit-identical to Java's, which
# forbids fused multiply-add contraction. Without it the two implementations
# drift in the last bits and the parity test fails for the wrong reason.

target="${1:-all}"
built=0

build_linux() {
  if ! command -v g++ > /dev/null; then
    echo "skip linux: no g++"
    return
  fi
  echo "building libairtillery_ballistics.so"
  # shellcheck disable=SC2086
  g++ $flags $jni_include -I"$JAVA_HOME/include/linux" \
      "$src" -o "$out/libairtillery_ballistics.so"
  built=1
}

build_windows() {
  local cxx=x86_64-w64-mingw32-g++
  if ! command -v $cxx > /dev/null; then
    echo "skip windows: no $cxx (apt install mingw-w64)"
    return
  fi
  echo "building airtillery_ballistics.dll"
  # win32 headers live under include/win32 in any JDK source tree; a Linux JDK
  # ships only include/linux, so the two headers it needs are synthesised here.
  local shim="$out/win32-shim"
  mkdir -p "$shim"
  cat > "$shim/jni_md.h" <<'EOF'
#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_
#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall
typedef long jint;
typedef __int64 jlong;
typedef signed char jbyte;
#endif
EOF
  # shellcheck disable=SC2086
  $cxx $flags $jni_include -I"$shim" -static-libgcc -static-libstdc++ \
      "$src" -o "$out/airtillery_ballistics.dll"
  built=1
}

case "$target" in
  linux)   build_linux ;;
  windows) build_windows ;;
  all)     build_linux; build_windows ;;
  *)       echo "usage: $0 [linux|windows|all]" >&2; exit 2 ;;
esac

if [[ $built -eq 0 ]]; then
  echo "nothing built" >&2
  exit 1
fi

ls -la "$out"/*.so "$out"/*.dll 2>/dev/null || true
