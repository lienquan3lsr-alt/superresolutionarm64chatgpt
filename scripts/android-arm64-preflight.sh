#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'PREFLIGHT ERROR: %s\n' "$*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -d modules/native/cpp && -d modules/common && -d modules/fabric ]]; then
    PREFIX='modules/'
elif [[ -d native/cpp && -d common && -d fabric ]]; then
    PREFIX=''
else
    fail 'Expected common/fabric/native at repository root or under modules/.'
fi

command -v java >/dev/null 2>&1 || fail 'JDK 25 is not on PATH.'
command -v cmake >/dev/null 2>&1 || fail 'CMake is not on PATH.'
command -v ninja >/dev/null 2>&1 || fail 'Ninja is not on PATH.'
command -v python3 >/dev/null 2>&1 || fail 'Python 3 is not on PATH.'

JAVA_VERSION="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version =/ {print $2; exit}')"
[[ "$JAVA_VERSION" == "25" ]] || fail "Expected Java 25, found '${JAVA_VERSION:-unknown}'."

CMAKE_VERSION="$(cmake --version | awk 'NR==1 {print $3}')"
python3 - "$CMAKE_VERSION" <<'PY'
import re, sys
version = tuple(map(int, re.findall(r'\d+', sys.argv[1])[:3]))
if version < (3, 22, 1):
    raise SystemExit(f'PREFLIGHT ERROR: CMake >= 3.22.1 required, found {sys.argv[1]}')
print(f'Preflight: CMake {sys.argv[1]} OK')
PY

: "${ANDROID_NDK_HOME:=${ANDROID_NDK_ROOT:-}}"
[[ -n "$ANDROID_NDK_HOME" ]] || fail 'ANDROID_NDK_HOME/ANDROID_NDK_ROOT is not set.'
[[ -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]] || fail "Android NDK toolchain missing under: $ANDROID_NDK_HOME"
NDK_REVISION="$(sed -n 's/^Pkg.Revision = //p' "$ANDROID_NDK_HOME/source.properties" 2>/dev/null | head -n1)"
[[ "$NDK_REVISION" == "28.0.12433566" || "$NDK_REVISION" == "28.0.12433566-beta1" ]] || fail "Expected Android NDK 28.0.12433566 or beta1, found '${NDK_REVISION:-unknown}'."
LLVM_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for tool in clang++ llvm-readelf llvm-nm; do
    [[ -x "$LLVM_BIN/$tool" ]] || fail "NDK tool missing: $LLVM_BIN/$tool"
done

NATIVE="${PREFIX}native/cpp"
COMMON="${PREFIX}common"
FABRIC="${PREFIX}fabric"
CONFIG="$ROOT/configs/1.21.11.json"
for file in \
  "$NATIVE/SRNativeMain/third_party/freetype/CMakeLists.txt" \
  "$NATIVE/SRNativeMain/third_party/glslang/CMakeLists.txt" \
  "$NATIVE/SRNativeMain/CMakeLists.txt" \
  "$NATIVE/CMakeLists.txt" \
  "$COMMON/src/main/java/io/homo/superresolution/core/SuperResolutionNative.java" \
  "$FABRIC/build.gradle.kts" \
  "$CONFIG" \
  gradle/wrapper/gradle-wrapper.jar \
  gradle/wrapper/gradle-wrapper.properties; do
    [[ -s "$file" ]] || fail "Required source/build file is missing: $file"
done

WRAPPER_URL="$(sed -n 's/^distributionUrl=//p' gradle/wrapper/gradle-wrapper.properties | sed 's/\\:/:/g')"
[[ "$WRAPPER_URL" == *'gradle-9.7.1-bin.zip' ]] || fail "Unexpected Gradle distribution URL: ${WRAPPER_URL:-missing}"

python3 - "$CONFIG" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    cfg = json.load(f)
common = cfg.get('common') or {}
platforms = common.get('platforms') or []
if 'fabric' not in platforms:
    raise SystemExit('PREFLIGHT ERROR: configs/1.21.11.json does not enable the Fabric platform.')
if common.get('java_version') != 25:
    raise SystemExit(f"PREFLIGHT ERROR: expected common.java_version=25, found {common.get('java_version')!r}.")
print('Preflight: Minecraft 1.21.11 Fabric + Java 25 config OK')
PY

TOP_CMAKE="$NATIVE/CMakeLists.txt"
MAIN_CMAKE="$NATIVE/SRNativeMain/CMakeLists.txt"
grep -Fq 'option(SR_FSR ' "$TOP_CMAKE" || fail 'SR_FSR option is missing from the native top-level CMakeLists.'
grep -Fq 'option(SR_XESS ' "$TOP_CMAKE" || fail 'SR_XESS option is missing from the native top-level CMakeLists.'
grep -Fq 'option(SR_NGX ' "$TOP_CMAKE" || fail 'SR_NGX option is missing from the native top-level CMakeLists.'
grep -Fq 'option(SR_STREAMLINE ' "$TOP_CMAKE" || fail 'SR_STREAMLINE option is missing from the native top-level CMakeLists.'
grep -Fq 'add_subdirectory("${GLSLANG_DIR}"' "$MAIN_CMAKE" || fail 'Android glslang source-build branch is missing.'
grep -Fq 'set(ENABLE_OPT OFF CACHE BOOL' "$MAIN_CMAKE" || fail 'Android glslang optimizer-off setting is missing.'
grep -Fq 'add_subdirectory("${FT_DIR}")' "$MAIN_CMAKE" || fail 'FreeType subdirectory is not wired.'
grep -Fq 'OUTPUT_NAME "SuperResolution+${LIB_PLATFORM}+${SR_BUILD_TYPE}"' "$MAIN_CMAKE" || fail 'Expected Android native output name is missing.'

printf '\nAndroid ARM64 preflight: PASS\n'
printf '  Layout: %s\n' "${PREFIX:-repository root}"
printf '  Java: %s\n' "$JAVA_VERSION"
printf '  CMake: %s\n' "$CMAKE_VERSION"
printf '  NDK: %s (%s)\n' "$ANDROID_NDK_HOME" "$NDK_REVISION"
printf '  ABI: arm64-v8a\n'
printf '  Native providers: FSR/FSR4/D3D12/XeSS/NGX/Streamline will be disabled for the core-only build\n'
printf '  Submodules: FreeType + glslang source trees present\n'
printf '\nPreflight verifies inputs only; it does not claim compilation or runtime interop passed.\n'
