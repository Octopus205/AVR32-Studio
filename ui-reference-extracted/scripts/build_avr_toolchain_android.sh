#!/usr/bin/env bash
set -euo pipefail

# OctoAVR32A - Cross-compile AVR-GCC 7.3.0 + Binutils 2.30 for Android NDK (Bionic)
# Produces Bionic-linked executables packaged as libavr_*.so in app/src/main/jniLibs/<abi>/
# and target-independent AVR sysroot (avr-libc, headers, crt*.o, libgcc.a, ldscripts)
# in app/src/main/assets/avr_sysroot.zip.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-/tmp/octoavr_toolchain_build}"
SRC_DIR="$WORK_DIR/src"
SYSROOT_URL="https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-x86_64-pc-linux-gnu.tar.bz2"

BINUTILS_VER="2.30"
GCC_VER="7.3.0"
GMP_VER="6.1.2"
MPFR_VER="4.0.1"
MPC_VER="1.1.0"

NDK_DIR="${ANDROID_NDK_HOME:-}"
if [[ -z "$NDK_DIR" ]]; then
  NDK_DIR="$(ls -d "${ANDROID_SDK_ROOT:-/opt/android/sdk}"/ndk/* 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$NDK_DIR" || ! -d "$NDK_DIR" ]]; then
  echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME or install ndk via sdkmanager." >&2
  exit 1
fi

TOOLCHAIN_HOST="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
JOBS="${JOBS:-$(nproc)}"
API_LEVEL="${API_LEVEL:-24}"
ABIS="${ABIS:-arm64-v8a x86_64}"

mkdir -p "$SRC_DIR" "$ROOT_DIR/app/src/main/assets"

download_and_extract() {
  local url="$1"
  local archive="$SRC_DIR/$(basename "$url")"
  if [[ ! -f "$archive" ]]; then
    echo "Downloading $url..."
    curl -fL --retry 3 -o "$archive" "$url"
  fi
  local dir_name
  set +o pipefail
  dir_name="$(tar -tf "$archive" | head -n 1 | cut -d/ -f1)"
  set -o pipefail
  if [[ -n "$dir_name" && ! -d "$SRC_DIR/$dir_name" ]]; then
    echo "Extracting $(basename "$archive")..."
    tar -xf "$archive" -C "$SRC_DIR"
  fi
}

# 1. Package AVR Sysroot (target-independent AVR-Libc headers, CRT, libgcc, ldscripts)
build_sysroot_asset() {
  local out_zip="$ROOT_DIR/app/src/main/assets/avr_sysroot.zip"
  if [[ -f "$out_zip" ]] && unzip -l "$out_zip" 2>/dev/null | grep -q "device-specs/specs-atmega32a"; then
    echo "Sysroot asset already valid: $out_zip ($(du -h "$out_zip" | cut -f1))"
    return 0
  fi
  echo "Preparing AVR sysroot asset..."
  download_and_extract "$SYSROOT_URL"
  local arduino_avr="$SRC_DIR/avr"
  local stage="$WORK_DIR/sysroot_stage"
  rm -rf "$stage"
  mkdir -p "$stage/avr/include" "$stage/avr/lib" "$stage/lib/gcc/avr/$GCC_VER"

  cp -a "$arduino_avr/avr/include/." "$stage/avr/include/"
  cp -a "$arduino_avr/avr/lib/ldscripts" "$stage/avr/lib/"

  # Copy root crt/libs + essential AVR architectures (avr5 covers atmega32a/328p/16a)
  for f in "$arduino_avr/avr/lib"/*.o "$arduino_avr/avr/lib"/*.a; do
    [[ -e "$f" ]] && cp -a "$f" "$stage/avr/lib/"
  done
  for arch in avr25 avr3 avr31 avr35 avr4 avr5 avr51 avr6; do
    if [[ -d "$arduino_avr/avr/lib/$arch" ]]; then
      cp -a "$arduino_avr/avr/lib/$arch" "$stage/avr/lib/"
    fi
    if [[ -d "$arduino_avr/lib/gcc/avr/$GCC_VER/$arch" ]]; then
      cp -a "$arduino_avr/lib/gcc/avr/$GCC_VER/$arch" "$stage/lib/gcc/avr/$GCC_VER/"
    fi
  done

  if [[ -d "$arduino_avr/lib/gcc/avr/$GCC_VER/device-specs" ]]; then
    cp -a "$arduino_avr/lib/gcc/avr/$GCC_VER/device-specs" "$stage/lib/gcc/avr/$GCC_VER/"
  fi
  if [[ -d "$arduino_avr/lib/gcc/avr/$GCC_VER/include" ]]; then
    cp -a "$arduino_avr/lib/gcc/avr/$GCC_VER/include" "$stage/lib/gcc/avr/$GCC_VER/"
  fi
  if [[ -d "$arduino_avr/lib/gcc/avr/$GCC_VER/include-fixed" ]]; then
    cp -a "$arduino_avr/lib/gcc/avr/$GCC_VER/include-fixed" "$stage/lib/gcc/avr/$GCC_VER/"
  fi
  for f in "$arduino_avr/lib/gcc/avr/$GCC_VER"/*.a "$arduino_avr/lib/gcc/avr/$GCC_VER"/*.o; do
    [[ -e "$f" ]] && cp -a "$f" "$stage/lib/gcc/avr/$GCC_VER/"
  done

  rm -f "$out_zip"
  (cd "$stage" && zip -qr -9 "$out_zip" .)
  echo "Sysroot packaged: $out_zip ($(du -h "$out_zip" | cut -f1))"
}

# 2. Download GNU sources for host cross-compilation
prepare_gnu_sources() {
  download_and_extract "https://mirrors.ocf.berkeley.edu/gnu/binutils/binutils-${BINUTILS_VER}.tar.xz"
  download_and_extract "https://mirrors.ocf.berkeley.edu/gnu/gcc/gcc-${GCC_VER}/gcc-${GCC_VER}.tar.xz"
  download_and_extract "https://mirrors.ocf.berkeley.edu/gnu/gmp/gmp-${GMP_VER}.tar.xz"
  download_and_extract "https://mirrors.ocf.berkeley.edu/gnu/mpfr/mpfr-${MPFR_VER}.tar.xz"
  download_and_extract "https://mirrors.ocf.berkeley.edu/gnu/mpc/mpc-${MPC_VER}.tar.gz"
}

build_for_abi() {
  local abi="$1"
  local host_triple clang_prefix strip_bin
  case "$abi" in
    arm64-v8a)
      host_triple="aarch64-linux-android"
      clang_prefix="aarch64-linux-android${API_LEVEL}"
      ;;
    x86_64)
      host_triple="x86_64-linux-android"
      clang_prefix="x86_64-linux-android${API_LEVEL}"
      ;;
    armeabi-v7a)
      host_triple="arm-linux-androideabi"
      clang_prefix="armv7a-linux-androideabi${API_LEVEL}"
      ;;
    *)
      echo "Unsupported ABI: $abi" >&2
      exit 1
      ;;
  esac

  export CC="$TOOLCHAIN_HOST/bin/${clang_prefix}-clang"
  export CXX="$TOOLCHAIN_HOST/bin/${clang_prefix}-clang++"
  export AR="$TOOLCHAIN_HOST/bin/llvm-ar"
  export RANLIB="$TOOLCHAIN_HOST/bin/llvm-ranlib"
  export LD="$TOOLCHAIN_HOST/bin/ld.lld"
  export STRIP="$TOOLCHAIN_HOST/bin/llvm-strip"
  export CFLAGS="-Os -fPIC -std=gnu11 -Wno-error -Wno-implicit-function-declaration -Wno-implicit-int -Wno-int-conversion -Wno-incompatible-function-pointer-types"
  export CXXFLAGS="-Os -fPIC -std=gnu++11 -Wno-register -Wno-error -Wno-narrowing -Wno-c++11-narrowing -Wno-deprecated-declarations"
  export LDFLAGS="-Wl,--gc-sections -static-libstdc++"

  local abi_build="$WORK_DIR/build_${abi}"
  local host_deps="$abi_build/deps"
  local prefix="$abi_build/install"
  rm -rf "$abi_build"
  mkdir -p "$abi_build/binutils" "$abi_build/gcc" "$host_deps" "$prefix"

  # Build static GMP / MPFR / MPC for Android host
  mkdir -p "$abi_build/gmp" "$abi_build/mpfr" "$abi_build/mpc"
  (
    cd "$abi_build/gmp"
    "$SRC_DIR/gmp-${GMP_VER}/configure" \
      --host="$host_triple" --prefix="$host_deps" \
      --disable-shared --enable-static --disable-assembly
    make -j"$JOBS" && make install
  )
  (
    cd "$abi_build/mpfr"
    "$SRC_DIR/mpfr-${MPFR_VER}/configure" \
      --host="$host_triple" --prefix="$host_deps" \
      --with-gmp="$host_deps" --disable-shared --enable-static
    make -j"$JOBS" && make install
  )
  (
    cd "$abi_build/mpc"
    "$SRC_DIR/mpc-${MPC_VER}/configure" \
      --host="$host_triple" --prefix="$host_deps" \
      --with-gmp="$host_deps" --with-mpfr="$host_deps" \
      --disable-shared --enable-static
    make -j"$JOBS" && make install
  )

  # Build binutils-avr
  (
    cd "$abi_build/binutils"
    "$SRC_DIR/binutils-${BINUTILS_VER}/configure" \
      --host="$host_triple" \
      --target=avr \
      --prefix="$prefix" \
      --disable-nls \
      --disable-werror \
      --disable-gdb \
      --disable-sim
    make -j"$JOBS"
    make install
  )

  # Build gcc-avr host binaries (all-gcc only; target libraries come from avr_sysroot.zip)
  (
    cd "$abi_build/gcc"
    "$SRC_DIR/gcc-${GCC_VER}/configure" \
      --host="$host_triple" \
      --target=avr \
      --prefix="$prefix" \
      --with-gmp="$host_deps" \
      --with-mpfr="$host_deps" \
      --with-mpc="$host_deps" \
      --enable-languages=c,c++ \
      --disable-nls \
      --disable-libssp \
      --disable-libada \
      --disable-shared \
      --disable-threads \
      --disable-bootstrap \
      --disable-lto \
      --disable-plugin \
      --disable-libgomp \
      --disable-libquadmath \
      --disable-decimal-float \
      --with-avrlibc=yes
    make -j"$JOBS" all-gcc
    make install-gcc
  )

  # Package binaries into app/src/main/jniLibs/<abi>/libavr_*.so
  local jni_out="$ROOT_DIR/app/src/main/jniLibs/$abi"
  mkdir -p "$jni_out"

  cp "$prefix/bin/avr-gcc" "$jni_out/libavr_gcc.so"
  cp "$prefix/bin/avr-g++" "$jni_out/libavr_gpp.so"
  cp "$prefix/bin/avr-as" "$jni_out/libavr_as.so"
  cp "$prefix/bin/avr-ld" "$jni_out/libavr_ld.so"
  cp "$prefix/bin/avr-ar" "$jni_out/libavr_ar.so"
  cp "$prefix/bin/avr-objcopy" "$jni_out/libavr_objcopy.so"
  cp "$prefix/bin/avr-objdump" "$jni_out/libavr_objdump.so"
  cp "$prefix/bin/avr-size" "$jni_out/libavr_size.so"
  cp "$prefix/libexec/gcc/avr/$GCC_VER/cc1" "$jni_out/libavr_cc1.so"
  cp "$prefix/libexec/gcc/avr/$GCC_VER/cc1plus" "$jni_out/libavr_cc1plus.so"
  cp "$prefix/libexec/gcc/avr/$GCC_VER/collect2" "$jni_out/libavr_collect2.so"

  "$STRIP" --strip-unneeded "$jni_out"/libavr_*.so
  chmod 0755 "$jni_out"/libavr_*.so
  echo "Packaged Android $abi AVR toolchain:"
  ls -lh "$jni_out"/libavr_*.so
}

build_sysroot_asset
prepare_gnu_sources
for abi in $ABIS; do
  build_for_abi "$abi"
done
echo "Done! All AVR toolchain assets and jniLibs ready."
