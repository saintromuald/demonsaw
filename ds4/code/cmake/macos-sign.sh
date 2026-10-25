#!/usr/bin/env bash
# Sign and notarize a Demonsaw.app for release distribution.
#
# Usage:
#   APPLE_SIGNING_IDENTITY="Developer ID Application: ..." \
#   APPLE_ID=you@example.com APPLE_APP_PASSWORD=xxxx-xxxx-xxxx-xxxx \
#   APPLE_TEAM_ID=XXXXXXXXXX \
#   ./macos-sign.sh path/to/Demonsaw.app
#
# - The app is signed with the hardened runtime (required for notarization).
# - Notarization runs only when APPLE_ID/APPLE_APP_PASSWORD/APPLE_TEAM_ID
#   are all set; otherwise the script just signs.
# - After this script, create the DMG and notarize it the same way with
#   `xcrun notarytool submit <dmg> ... --wait` + `xcrun stapler staple <dmg>`.
set -euo pipefail

APP="${1:?usage: macos-sign.sh <Demonsaw.app>}"
IDENTITY="${APPLE_SIGNING_IDENTITY:?set APPLE_SIGNING_IDENTITY to the Developer ID Application certificate name}"

if [ ! -d "$APP/Contents" ]; then
	echo "error: $APP is not an .app bundle" >&2
	exit 1
fi

echo "Signing frameworks and libraries in $APP"
if [ -d "$APP/Contents/Frameworks" ]; then
	while IFS= read -r -d '' fw; do
		codesign --force --timestamp --sign "$IDENTITY" "$fw"
	done < <(find "$APP/Contents/Frameworks" -maxdepth 1 -name "*.framework" -print0)
fi
while IFS= read -r -d '' lib; do
	codesign --force --timestamp --sign "$IDENTITY" "$lib"
done < <(find "$APP/Contents" -name "*.dylib" -print0)

echo "Signing app bundle"
codesign --force --timestamp --options runtime --sign "$IDENTITY" "$APP"
codesign --verify --deep --strict "$APP"

if [ -n "${APPLE_ID:-}" ] && [ -n "${APPLE_APP_PASSWORD:-}" ] && [ -n "${APPLE_TEAM_ID:-}" ]; then
	echo "Submitting for notarization"
	ditto -c -k --keepParent "$APP" /tmp/demonsaw-app.zip
	xcrun notarytool submit /tmp/demonsaw-app.zip \
		--apple-id "$APPLE_ID" \
		--password "$APPLE_APP_PASSWORD" \
		--team-id "$APPLE_TEAM_ID" \
		--wait
	echo "Stapling"
	xcrun stapler staple "$APP"
	xcrun stapler validate "$APP"
	echo "Done: $APP is signed, notarized and stapled."
else
	echo "Done: $APP is signed. (Notarization skipped: set APPLE_ID/APPLE_APP_PASSWORD/APPLE_TEAM_ID)"
fi
