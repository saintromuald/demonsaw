#!/usr/bin/env bash
# Deterministic Qt deployment for Demonsaw.app.
#
# macdeployqt normally does this, but its otool QProcess times out when the
# x86_64 Qt tools run under Rosetta on arm64 CI runners (the first otool
# translation takes ~30s and macdeployqt kills it). This script copies the
# four Qt frameworks and the plugins the app needs and rewrites install
# names with install_name_tool - no QProcess, no otool, no timeouts.
#
# Usage: macdeploy-manual.sh <Qt prefix> <Demonsaw.app>
#   where <Qt prefix> contains lib/QtCore.framework ... and plugins/
set -euo pipefail

QTPREFIX="${1:?usage: macdeploy-manual.sh <qt-prefix> <Demonsaw.app>}"
APP="${2:?usage: macdeploy-manual.sh <qt-prefix> <Demonsaw.app>}"

FW_DIR="$APP/Contents/Frameworks"
PLUG_DIR="$APP/Contents/PlugIns"
RES_DIR="$APP/Contents/Resources"
EXE="$APP/Contents/MacOS/Demonsaw"

mkdir -p "$FW_DIR" "$PLUG_DIR/platforms" "$PLUG_DIR/styles" "$PLUG_DIR/imageformats"

# --- Frameworks -------------------------------------------------------------
for fw in QtCore QtGui QtWidgets QtSvg; do
	src="$QTPREFIX/lib/$fw.framework"
	[ -d "$src" ] || { echo "missing $src" >&2; exit 1; }
	rm -rf "$FW_DIR/$fw.framework"
	cp -R "$src" "$FW_DIR/$fw.framework"

	bin="$FW_DIR/$fw.framework/Versions/5/$fw"
	install_name_tool -id "@rpath/$fw.framework/Versions/5/$fw" "$bin"

	# Rewrite references to the other Qt frameworks
	for dep in QtCore QtGui QtWidgets QtSvg; do
		old="$QTPREFIX/lib/$dep.framework/Versions/5/$dep"
		new="@rpath/$dep.framework/Versions/5/$dep"
		install_name_tool -change "$old" "$new" "$bin" 2>/dev/null || true
	done
done

# --- Plugins ----------------------------------------------------------------
copy_plugin()
{
	local src="$1" dest="$2"
	[ -f "$src" ] || { echo "missing plugin $src" >&2; exit 1; }
	cp "$src" "$dest"
	# The app finds frameworks relative to the executable
	install_name_tool -add_rpath "@executable_path/../Frameworks" "$dest" 2>/dev/null || true
	install_name_tool -add_rpath "@loader_path/../../Frameworks" "$dest" 2>/dev/null || true
	for dep in QtCore QtGui QtWidgets QtSvg; do
		install_name_tool -change \
			"$QTPREFIX/lib/$dep.framework/Versions/5/$dep" \
			"@rpath/$dep.framework/Versions/5/$dep" \
			"$dest" 2>/dev/null || true
	done
}

# Copy every platform/style/imageformat plugin: libqcocoa is required, the
# app renders SVG logos and PNG/ICO assets, and the style plugin provides
# the native macOS widget style.
for dir in platforms styles imageformats; do
	mkdir -p "$PLUG_DIR/$dir"
	for pl in "$QTPREFIX/plugins/$dir"/*.dylib; do
		[ -f "$pl" ] || continue
		copy_plugin "$pl" "$PLUG_DIR/$dir/$(basename "$pl")"
	done
done

# --- App binary -------------------------------------------------------------
for dep in QtCore QtGui QtWidgets QtSvg; do
	install_name_tool -change \
		"$QTPREFIX/lib/$dep.framework/Versions/5/$dep" \
		"@rpath/$dep.framework/Versions/5/$dep" \
		"$EXE"
done
install_name_tool -add_rpath "@executable_path/../Frameworks" "$EXE" 2>/dev/null || true
install_name_tool -add_rpath "@executable_path/../PlugIns" "$EXE" 2>/dev/null || true

# --- qt.conf ----------------------------------------------------------------
cat > "$RES_DIR/qt.conf" <<EOF
[Paths]
Plugins = PlugIns
EOF

echo "Deployed Qt frameworks and plugins into $APP"
