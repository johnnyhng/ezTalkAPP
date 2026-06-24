# LiteRT-LM on Pixel 10 Pro XL: NPU initialization incident

## Scope

This document records the Android NPU initialization failure investigated on
2026-06-22 through 2026-06-25.

- Device: Pixel 10 Pro XL (`mustang`)
- OS: Android 16 / API 36
- Build: `google/mustang/mustang:16/CP1A.260505.005/15081906:user/release-keys`
- LiteRT-LM Android dependency: `0.13.1`
- Model: `gemma-4-E2B-it_Google_Tensor_G5/model.litertlm`
- Model container version reported by LiteRT-LM: `1.5.0`

## Symptoms and root causes

The failure occurred in three distinct stages.

### Stage 1: no LiteRT Dispatch implementation

The NPU engine was created with `Backend.NPU(nativeLibraryDir)`, but the APK
contained only the LiteRT-LM JNI, LiteRT core, and GPU accelerator libraries.
Initialization ended with:

```text
No dispatch library found in .../lib/arm64
No usable Dispatch runtime found
Fatal signal 6 (SIGABRT)
```

The Google Tensor dispatch implementation was built from LiteRT-LM checkout
`a2997d83721335fc9e2d3856c503b8a88829c82c` and packaged as:

```text
app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_GoogleTensor.so
```

Binary identification:

```text
ELF: ARM64/AArch64
Build ID: 4eac84d03fe32da79283642d8669d307
SHA-256: bc0f3e59b8e2555da6d29aa4c7d44898ed938b3232449850731ecc7435e92453
Export: LiteRtDispatchGetApi@VERS_1.0
```

The source artifact was produced at:

```text
/home/hhs/workspace/LiteRT-LM/bazel-bin/external/litert/litert/vendors/google_tensor/dispatch/libLiteRtDispatch_GoogleTensor.so
```

The repository's `app/src/main/jniLibs/.gitignore` ignores `*.so`, so this
artifact must be staged explicitly with `git add -f` when it changes.

### Stage 2: SouthBound runtime inaccessible

After packaging the dispatch implementation, LiteRT found it but failed during
SouthBound initialization:

```text
Loading shared library: .../libLiteRtDispatch_GoogleTensor.so
Failed to initialize SB
No usable Dispatch runtime found
Fatal signal 6 (SIGABRT)
```

The Google Tensor dispatch implementation late-binds the system library with:

```cpp
dlopen("libedgetpu_litert.so", RTLD_NOW | RTLD_GLOBAL)
```

ADB confirmed that the Pixel system provides and publishes the runtime:

```text
/vendor/lib64/libedgetpu_litert.so
/vendor/etc/public.libraries.txt: libedgetpu_litert.so
dumpsys package libraries: libedgetpu_litert.so -> (so) libedgetpu_litert.so
```

However, the installed ezTalk package did not list it under
`usesOptionalNativeLibraries`. Because the app targets API 34, Android 12+
does not expose a vendor non-NDK library to the app linker namespace unless it
is declared inside the manifest's `<application>` element.

The fix is:

```xml
<uses-native-library
    android:name="libedgetpu_litert.so"
    android:required="false" />
```

It remains optional so installation and non-NPU execution can still work on
devices that do not publish this Pixel-specific library.

### Stage 3: concurrent generation callback crash

After the dispatch and SouthBound fixes, the NPU engine initialized
successfully:

```text
SouthBound context created.
LiteRT-LM Engine initialized successfully with NPU backend.
```

The app then started its word and sentence prediction requests at nearly the
same time. Two conversations reached `sendMessageAsync`, followed by a native
callback-thread crash:

```text
Creating Gemma4DataProcessor
Creating Gemma4DataProcessor
Fatal signal 11 (SIGSEGV) ... callback_thread
```

LiteRT-LM 0.13.1 implements its Kotlin Flow adapter with `callbackFlow` and an
empty `awaitClose` handler. Cancelling Flow collection therefore does not
cancel the native inference. The provider previously closed the conversation
in `finally`, which could release its native handle while a callback was still
running. Concurrent conversations also increased the likelihood of exposing
this lifetime race.

The provider now applies two safeguards around the complete generation
lifecycle:

- A per-provider `Mutex` permits only one active conversation at a time.
- `NonCancellable` keeps an in-flight collection alive until the native
  callback finishes, after which the conversation can be closed safely.

Requests cancelled while waiting for the mutex still stop normally; only a
request that has already entered native inference is allowed to finish.

The device also reported SouthBound version `0.14` while the dispatch library
requires `>=0.18` for `edgetpu_performance_mode`. LiteRT skipped that optional
performance hint and continued initialization. If a serialized generation
still crashes, dispatch/SouthBound version alignment is the next issue to
investigate.

## Build, install, and verification

Build and confirm both native artifacts:

```bash
./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | \
  grep -E 'libLiteRtDispatch_GoogleTensor|liblitertlm_jni'
```

Confirm the merged manifest contains the system runtime declaration:

```bash
$ANDROID_HOME/build-tools/35.0.0/aapt dump xmltree \
  app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml | \
  grep -A4 libedgetpu_litert.so
```

The debug APK is large, so wireless ADB streaming may time out. A non-streaming
replacement install preserves the downloaded model and app data:

```bash
adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk
```

Verify PackageManager applied the native-library namespace update:

```bash
adb shell dumpsys package tw.com.johnnyhng.eztalk.asr | \
  grep -A12 usesOptionalNativeLibraries
```

The expected output includes both sections:

```text
usesOptionalNativeLibraries:
  libedgetpu_litert.so
usesLibraryFiles:
  libedgetpu_litert.so
```

## Operational notes

- Android Studio Apply Changes is insufficient for native-library packaging or
  manifest namespace changes. Install a rebuilt APK and restart the process.
- Avoid uninstalling unless data removal is intended; the downloaded model is
  approximately 4 GB. Prefer `adb install --no-streaming -r -t`.
- LiteRT-LM 0.13.1 aborts the native process on this initialization failure.
  Kotlin `try/catch` cannot recover from `SIGABRT`, so a GPU/CPU fallback after
  a failed NPU initialization is not reliable.
- The initial `NativeLibraryLoader.nativeCheckLoaded()` lookup error is not the
  root cause when the following log confirms `liblitertlm_jni.so` loaded.
- Word and sentence prediction requests can arrive together. The provider
  serializes their complete native generation lifecycle; do not remove this
  guard unless the runtime's cancellation and concurrency behavior is verified.

## Troubleshooting commands

```bash
adb shell ls -l /vendor/lib64/libedgetpu_litert.so
adb shell cat /vendor/etc/public.libraries.txt
adb shell dumpsys package libraries
adb logcat -d | grep -iE \
  'edgetpu|southbound|thrInitialize|linker|namespace|dlopen|avc: denied|dispatch'
```
