# Demonsaw for iOS

Native iOS port of Demonsaw 4.2.1, built on the same headless engine as the
Android app (`ds4/code/android/engine`) - chat, rooms, private messages,
browse, search, shares, uploads/downloads with progress and cancellation.
The wire protocol and crypto are unchanged, so iOS clients interoperate
with desktop, Android and CLI clients and with any Demonsaw router.

## Layout

```
ios/                          SwiftUI app (XcodeGen project.yml)
  Demonsaw/
    Native.swift              C bridge surface (same 5 calls as Native.kt)
    AppViewModel.swift        single-threaded engine driver + snapshot polling
    Model.swift               JSON snapshot decoding (port of Model.kt)
    UI/                       SwiftUI: Chat/Rooms/People/Browse/Transfers/Settings
ds4/code/
  ios/engine_bridge.{h,cpp}   extern "C" bridge (same surface as the JNI layer)
  CMakeLists.txt              DEMONSAW_BUILD_IOS builds the engine static lib
scripts/build-ios-xcframework.sh   device + simulator XCFramework build
.github/workflows/build-ios.yml    CI: engine + app build + simulator smoke test
```

## Building on a Mac

Requirements: Xcode 15+, CMake 3.20+, Ninja, XcodeGen (`brew install
xcodegen`), and a Boost 1.69 source tree.

```bash
# 1. Boost 1.69 sources (the exact version the code was written against)
curl -fsSL -o boost_1_69_0.tar.bz2 \
  https://archives.boost.io/release/1.69.0/source/boost_1_69_0.tar.bz2
tar xjf boost_1_69_0.tar.bz2

# 2. Build the engine XCFramework (device arm64 + simulator arm64/x86_64)
bash scripts/build-ios-xcframework.sh \
  /path/to/boost_1_69_0 ios/build-ios

# 3. Generate the Xcode project and build
cd ios
xcodegen generate
xcodebuild -project Demonsaw.xcodeproj -scheme Demonsaw \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

For a device install, open `ios/Demonsaw.xcodeproj`, select your team in
Signing & Capabilities, and run.

## CI

`.github/workflows/build-ios.yml` builds the XCFramework, generates the
project, compiles the app for the simulator, boots an iPhone simulator,
installs and launches the app, captures a screenshot, and uploads:

- `DemonsawEngine.xcframework.zip` - the engine (device + simulator slices)
- `Demonsaw-ios-simulator.zip` - the built app bundle
- `simulator-smoke.png` - screenshot of the running app

## iOS platform notes (vs Android)

- **Background**: iOS suspends backgrounded apps and raw P2P sockets cannot
  run in the background, so the app operates while foregrounded. There is no
  Android-style foreground service; don't add VoIP-mode tricks - App Store
  review rejects them.
- **Files**: shares live in the app sandbox (pick folders with the document
  picker); downloads go to the app's Documents folder, which is exposed in
  the Files app (`UIFileSharingEnabled`).
- **Local network**: connecting to a router on your LAN triggers the iOS
  local network permission prompt (`NSLocalNetworkUsageDescription` is set).

## Distribution

TestFlight and App Store distribution require an Apple Developer Program
account ($99/year). Set your team in Xcode, archive the Release scheme, and
submit from the Organizer. The app is free and open source (MIT); Apple
charges no listing fee and no commission for free apps without in-app
purchases.
