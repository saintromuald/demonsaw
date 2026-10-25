#!/usr/bin/env bash
# Build DemonsawEngine.xcframework (the headless Demonsaw engine for iOS).
#
# Usage: build-ios-xcframework.sh <boost_1_69_0_dir> [output_dir]
#
# Requires Xcode command line tools, CMake and Ninja. Produces:
#   <output_dir>/DemonsawEngine.xcframework  - device (arm64) + simulator
#                                              (arm64, x86_64) slices
set -euo pipefail

BOOST="${1:?usage: build-ios-xcframework.sh <boost_1_69_0_dir> [output_dir]}"
OUT="${2:-$PWD/build-ios}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$REPO_ROOT/ds4/code"
BRIDGE_HEADER="$SRC/ios/engine_bridge.h"

IOS_MIN="13.0"

echo "Boost: $BOOST"
echo "Output: $OUT"

# ---------------------------------------------------------------------------
# Build the static library for one SDK
# ---------------------------------------------------------------------------
build_sdk()
{
	local sdk="$1"
	local archs="$2"
	local dir="$3"

	echo "=== Building for $sdk ($archs) ==="
	cmake -S "$SRC" -B "$dir" -G Ninja \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_SYSTEM_NAME=iOS \
		-DCMAKE_OSX_SYSROOT="$sdk" \
		-DCMAKE_OSX_ARCHITECTURES="$archs" \
		-DCMAKE_OSX_DEPLOYMENT_TARGET="$IOS_MIN" \
		-DDEMONSAW_BOOST_SOURCE_DIR="$BOOST" \
		-DDEMONSAW_BUILD_APPS=OFF \
		-DDEMONSAW_BUILD_IOS=ON \
		-DDEMONSAW_BUILD_TESTS=OFF
	cmake --build "$dir" --parallel 4
}

build_sdk iphoneos arm64 "$OUT/device"
build_sdk iphonesimulator "arm64;x86_64" "$OUT/simulator"

# ---------------------------------------------------------------------------
# Wrap each archive in a static framework, then make the XCFramework
# ---------------------------------------------------------------------------
make_framework()
{
	local lib="$1"
	local dir="$2"

	mkdir -p "$dir/DemonsawEngine.framework/Headers"
	cp "$BRIDGE_HEADER" "$dir/DemonsawEngine.framework/Headers/engine_bridge.h"
	cp "$lib" "$dir/DemonsawEngine.framework/DemonsawEngine"

	cat > "$dir/DemonsawEngine.framework/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleExecutable</key>
	<string>DemonsawEngine</string>
	<key>CFBundleIdentifier</key>
	<string>com.demonsaw.engine</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>DemonsawEngine</string>
	<key>CFBundlePackageType</key>
	<string>FMWK</string>
	<key>CFBundleShortVersionString</key>
	<string>4.2.1</string>
	<key>CFBundleVersion</key>
	<string>1</string>
</dict>
</plist>
EOF
}

make_framework "$OUT/device/libdemonsaw_engine.a" "$OUT"
make_framework "$OUT/simulator/libdemonsaw_engine.a" "$OUT/simulator"

echo "=== Creating DemonsawEngine.xcframework ==="
rm -rf "$OUT/DemonsawEngine.xcframework"
xcodebuild -create-xcframework \
	-framework "$OUT/DemonsawEngine.framework" \
	-framework "$OUT/simulator/DemonsawEngine.framework" \
	-output "$OUT/DemonsawEngine.xcframework"

echo "Done: $OUT/DemonsawEngine.xcframework"
