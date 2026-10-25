# Demonsaw for Android

Native Android port of Demonsaw 4.2.1, built on the original Qt-free C++ core
(`4_core`, `4_core_client`). The desktop UI is replaced with a Kotlin/Compose
app that talks to a small JNI layer; the wire protocol and crypto are unchanged,
so Android clients interoperate with desktop clients and routers.

## Feature parity with the desktop client

Verified end-to-end against desktop clients and our own router:

- Group chat (both directions), rooms (join/leave/chat), private messages
- Client list with mute/unmute
- Browse nested shares, download files and whole folders
- Full file transfers (uploads and downloads) with progress, queue,
  cancellation and finished history - byte-verified with matching hashes
- Search across shared files
- Share folders/files from the phone; desktop peers can download them
- Error log, connection status, group id display
- Foreground service keeps the connection and transfers alive in the
  background (Android 13+ notification permission is requested at startup)

Known limitations: folder shares from the phone need "All files access"
(granted from Settings; the app links to the system screen). Transfers use
the app's own storage unless that permission is granted.

## Layout

```
android/                     Gradle project (Kotlin + Compose)
  app/src/main/java/org/demonsaw/app/
    Native.kt                JNI surface
    AppViewModel.kt          single-threaded engine driver + snapshot polling
    MainActivity.kt          Compose UI (connect / chat / people / settings)
ds4/code/
  CMakeLists.txt             builds core, client, CLI, router (qmake replaced)
  cmake/BoostSources.cmake   compiles the Boost subset from source (Android)
  android/engine/            headless client wrapper around cli_application
  android/jni/               JNI shared library (libdemonsaw.so)
  android/engine/engine_test.cpp  host-side smoke test (two-client chat)
```

## Building

### Native library (arm64-v8a and x86_64)

```bash
# 1. Boost 1.69 sources (the exact version the code was written against)
curl -L -o boost_1_69_0.tar.bz2 https://archives.boost.io/release/1.69.0/source/boost_1_69_0.tar.bz2
tar xjf boost_1_69_0.tar.bz2

# 2. Configure with the Android NDK
cmake -S ds4/code -B build-android -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
  -DDEMONSAW_BOOST_SOURCE_DIR=/path/to/boost_1_69_0 \
  -DDEMONSAW_BUILD_JNI=ON

# 3. Build, strip, and drop into the app
ninja -C build-android demonsaw
llvm-strip --strip-unneeded build-android/jni/libdemonsaw.so
cp build-android/jni/libdemonsaw.so android/app/src/main/jniLibs/arm64-v8a/
```

### APK

```bash
cd android && ./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Host builds (router / CLI / tests)

```bash
cmake -S ds4/code -B build-host -G Ninja \
  -DDEMONSAW_BOOST_ROOT=/path/to/boost_1_69_0 \
  -DDEMONSAW_BUILD_TESTS=ON
ninja -C build-host demonsaw_router demonsaw_cli demonsaw_engine_test
```

## Fixes carried in this fork

- `CMakeLists.txt` — reproducible build replacing the qmake files
  (`ROOT=/demonsaw_4` hardcoding, missing Linux Boost binaries)
- Boost thread build fix for glibc >= 2.34 (`PTHREAD_STACK_MIN` macro)
- `url.cpp` — `signed char` decode table (plain `char` is unsigned on ARM)
- `prime.cpp` — missing `<stdexcept>` include
- Router console loop no longer spins on EOF (daemon-friendly, e.g. systemd)
- Router now relays group messages to every group member; the original code
  dropped the message for all recipients when the `self` flag was unset
- `-fno-strict-aliasing` — Crypto++ 7.0 relies on type punning; without it
  GCC 16 miscompiles `StringSink`/`SecByteBlock` at `-O2` and the router
  segfaults when a client connects
- Callback handlers take arguments by value (the callback dispatcher
  compares `std::function` types, so const-ref signatures never matched)
