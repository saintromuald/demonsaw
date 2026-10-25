# Demonsaw for macOS

Native macOS build of Demonsaw 4.2.1 — the **same desktop client** that ships
on Windows and Linux (`4_client_desktop` Qt Widgets UI, `demonsaw_cli`,
`demonsaw_router`), so it has full feature parity by construction: chat,
rooms, private messages, browse, search, uploads/downloads, shares, and
router mode. The wire protocol and crypto are unchanged; macOS clients
interoperate with Windows, Linux and Android clients.

## Layout

```
ds4/code/
  CMakeLists.txt             builds core, client, GUI, CLI, router, tests
  cmake/BoostSources.cmake   compiles the Boost 1.69 subset from source
  cmake/macOS-Info.plist.in  .app bundle Info.plist template
  cmake/macos-sign.sh        Developer ID signing + notarization helper
.github/workflows/build-macos.yml  CI: universal DMG on macOS runners
```

## Building on a Mac

Requirements: macOS 11+, Xcode command line tools, CMake 3.20+, Ninja,
Qt 5.15.x (Widgets + Svg), and a Boost 1.69 source tree.

```bash
# 1. Boost 1.69 sources (the exact version the code was written against)
curl -fsSL -o boost_1_69_0.tar.bz2 \
  https://archives.boost.io/release/1.69.0/source/boost_1_69_0.tar.bz2
tar xjf boost_1_69_0.tar.bz2

# 2. Qt 5.15.x (e.g. the official online installer, or Homebrew's qt@5,
#    or aqtinstall:  python3 -m aqt install-qt mac desktop 5.15.2 -m qtsvg)

# 3. Configure
#    Universal (x86_64 + arm64, macOS 11+):  add -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"
#    Intel-only (macOS 10.15+):              add -DCMAKE_OSX_DEPLOYMENT_TARGET=10.15
cmake -S ds4/code -B build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH=/path/to/Qt/5.15.2/clang_64 \
  -DDEMONSAW_BOOST_SOURCE_DIR=/path/to/boost_1_69_0 \
  -DDEMONSAW_BUILD_GUI=ON

# 4. Build
cmake --build build --parallel

# 5. Bundle Qt frameworks into the .app
/path/to/Qt/5.15.2/clang_64/bin/macdeployqt build/Demonsaw.app
# (if macdeployqt fails - e.g. its otool call times out under Rosetta on an
#  Apple Silicon machine - use the deterministic fallback instead:)
bash ds4/code/cmake/macdeploy-manual.sh /path/to/Qt/5.15.2/clang_64 build/Demonsaw.app

# 6. Ad-hoc sign so it runs locally without warnings
codesign --force --deep --sign - build/Demonsaw.app
```

Outputs:

- `build/Demonsaw.app` — the desktop client
- `build/demonsaw_cli` — command-line client
- `build/demonsaw_router` — router (runs a headless router daemon)

## CI builds

`.github/workflows/build-macos.yml` builds on GitHub's macOS runners and
uploads three artifact sets:

- `Demonsaw-macos-universal` — a universal (x86_64 + arm64) DMG for macOS 11+
  (two per-arch app bundles merged with `lipo`)
- `Demonsaw-macos-x86_64` — Intel-only app (macOS 10.15+, for old Macs)
- `Demonsaw-macos-arm64` — Apple Silicon app (macOS 11+)

Each build runs smoke tests (engine, CLI, GUI launch) on native hardware,
plus an end-to-end test that starts a local router and verifies an engine
client joins the group and its chat is relayed back. The merge job re-runs
the GUI launch on both archs (arm64 native and x86_64 under Rosetta) and
verifies every critical Mach-O is universal before building the DMG.

> Why two Qt installs: the official open-source Qt 5.15.2 macOS binaries are
> x86_64-only (Qt 5 never shipped arm64 prebuilts). The Intel slice builds
> against those; the arm64 slice builds against Homebrew's `qt@5`; the two
> bundles are merged with `lipo`. Qt guarantees binary compatibility across
> 5.15.x patch releases, so the mixed slices link cleanly.

## Release signing

CI DMGs are ad-hoc signed: fine for local use and testing, but Gatekeeper
will warn when other people download them. For a real release, sign with a
Developer ID Application certificate and notarize (requires an Apple
Developer Program account, $99/year):

```bash
APPLE_SIGNING_IDENTITY="Developer ID Application: Your Name (TEAMID)" \
APPLE_ID=you@example.com \
APPLE_APP_PASSWORD=xxxx-xxxx-xxxx-xxxx \
APPLE_TEAM_ID=TEAMID \
  ds4/code/cmake/macos-sign.sh build/Demonsaw.app

# then build the DMG from the signed app and notarize the DMG:
xcrun notarytool submit Demonsaw.dmg --apple-id you@example.com \
  --password xxxx-xxxx-xxxx-xxxx --team-id TEAMID --wait
xcrun stapler staple Demonsaw.dmg
```

## Notes

- The codebase uses `#if _MACX` platform guards; the CMake build defines
  `_MACX` automatically on Apple targets.
- Minimum macOS: 11.0 for universal builds (arm64 requirement). Use
  `-DCMAKE_OSX_DEPLOYMENT_TARGET=10.15` for Intel-only builds.
- Qt is linked per the LGPL; `macdeployqt` copies the Qt frameworks into the
  bundle so no separate Qt install is needed.
- Linux host builds need the Boost `PTHREAD_STACK_MIN` shadow patch that
  `cmake/BoostSources.cmake` applies automatically (glibc >= 2.34 only;
  macOS is unaffected).
