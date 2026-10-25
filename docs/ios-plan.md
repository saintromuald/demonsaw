# iOS plan of attack

Target: Demonsaw on iOS with feature parity with the desktop client, to the
extent iOS allows. Status: **planning** (macOS desktop build is done first;
see README-osx.md).

## What we already have (from the Android port)

- `ds4/code/android/engine/engine.{h,cpp}` — headless client engine wrapping
  `cli_application`: 17 commands (chat, pm, rooms, browse, mute, shares,
  downloads, search, ...) and a JSON snapshot the UI polls. Single-threaded
  API. **100% portable** — no JNI, no Android APIs, no non-POSIX syscalls.
- `cmake/BoostSources.cmake` — compiles the Boost 1.69 subset from source;
  already proven with libc++ on Android's NDK clang (same standard library
  family as iOS).
- The CMake core libs (`demonsaw_core`, `demonsaw_client_core`,
  `demonsaw_cryptopp`, `demonsaw_cppnetlib`) are Qt-free and platform-neutral.
- Android front end: JNI shim (~70 lines), `AppViewModel.kt` (single-threaded
  engine driver + config builder + snapshot polling), `Model.kt` (JSON
  decoding), `MainActivity.kt` (Compose UI, ~1100 lines).

## The iOS shape

Mirror the Android architecture exactly:

```
ios/                          Xcode project (SwiftUI app)
  Demonsaw/
    NativeBridge.swift        wraps the C bridge (same 5 calls as Native.kt)
    AppViewModel.swift        single-threaded engine driver + snapshot polling
    Model.swift               JSON snapshot decoding (port of Model.kt)
    UI/                       SwiftUI: Chat/Rooms/People/Transfers/Settings
ds4/code/
  ios/engine_bridge.cpp       extern "C" shim (same surface as JNI, no jni.h)
  CMakeLists.txt              + DEMONSAW_BUILD_IOS option (engine as xcframework)
```

### Step 1 — Engine as an XCFramework (few days)

- Extend the CMake build with an iOS path: `-DCMAKE_SYSTEM_NAME=iOS`,
  `-DCMAKE_OSX_ARCHITECTURES=arm64`, `-DCMAKE_OSX_SYSROOT=iphoneos` (plus an
  `iphonesimulator` pass). The Boost subset, Crypto++ (`CRYPTOPP_DISABLE_ASM`
  already set — no arm64 asm issues), cpp-netlib and the core all compile for
  iOS exactly like they did for Android (POSIX + libc++).
- Build `demonsaw_engine` (engine.cpp + cli_application.cpp + the C shim) for
  device and simulator, then `xcodebuild -create-xcframework`.
- CI: a `build-ios.yml` job on the macOS runner produces the xcframework
  (no signing needed for static libs) and uploads it.

### Step 2 — Swift bridge (few days)

- `engine_bridge.cpp`: the JNI file rewritten as plain `extern "C"`
  (`demonsaw_start/stop/snapshot/command/isRunning`), engine.cpp untouched.
- Swift wrapper replicating `AppViewModel.kt`: single-threaded dispatch,
  TOML config builder (port of `buildConfig()`), 250 ms snapshot polling,
  default download path = app Documents.

### Step 3 — SwiftUI app (1-2 weeks)

- Port `MainActivity.kt` to SwiftUI: connect screen, Chat/Rooms/People/
  Transfers/Settings tabs, room & PM screens, browse navigation, search,
  share management, transfer queue/progress/cancel/finished.
- Same state model: poll snapshots, issue commands, render JSON state.

### Step 4 — iOS platform work (1-2 weeks)

- **Local network**: `NSLocalNetworkUsageDescription` + the iOS 14+ local
  network permission prompt; router discovery over UDP multicast works from
  the client side.
- **Filesystem**: no shared filesystem on iOS. Shares point into the app
  sandbox; expose the Documents folder via the Files app
  (`UISupportsDocumentBrowser` / `LSSupportsOpeningDocumentsInPlace`), add a
  share extension and `UIDocumentPickerViewController` so users can share
  files from other apps and download to Files.
- **Background — the hard ceiling**: iOS suspends backgrounded apps and raw
  P2P sockets cannot run in the background (only HTTP via background
  URLSession, which does not fit the wire protocol). Desktop/Android-style
  "stay connected 24/7" and background transfers are **not possible**.
  Parity scope: full feature set while foregrounded; optional "keep screen
  on while transferring" and last-state restore on launch. Do NOT try the
  VoIP/audio background-mode tricks — App Store review rejects them as
  misuse.
- **ATS**: demonsaw speaks its own protocol over plain TCP, not NSURLSession,
  so no ATS exceptions are needed; verify nothing in the engine uses
  NSURLSession under the hood.

### Step 5 — Distribution (1 week + review time)

- Apple Developer Program: **$99/year** (required; no open-source discount).
  No per-app listing fee, 0% commission for a free app without IAP.
- TestFlight beta via the same account, then App Store submission.
- Review risk: Apple bans BitTorrent-style clients; Demonsaw is a
  client/router mesh, not a torrent client, and file-sync/p2p apps exist on
  the store (e.g. Möbius Sync), so approval is plausible — but expect
  questions about "anonymous file sharing". Prepare a plain-language
  description emphasizing encrypted personal/group sharing, and avoid
  piracy framing. Budget for at least one rejection/appeal cycle.

## Estimate

4-6 weeks of engineering to a TestFlight build with desktop feature parity
minus the iOS-impossible bits (background operation, arbitrary file system
access), plus App Store review time. The engine itself needs no changes
beyond the build system; the work is the SwiftUI app and the platform
permissions.

## Open questions before starting

1. Account: whose Apple Developer account ($99/yr)? Individual is fine.
2. Scope: is foreground-only acceptable for v1 (vs. trying background tricks
   that risk rejection)?
3. Bundle id (e.g. `com.demonsaw.ios`) and whether the iOS client should
   also expose router hosting (no — hosting needs background; keep
   client-only on iOS).
