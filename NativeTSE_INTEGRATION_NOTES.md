# NativeTSE Integration Notes

## Outcome

`HomeScreen` now initializes `NativeTSE` by copying `voice_filter_int8.onnx` and `dvector.bin` from assets into `cacheDir`, then calling `NativeTSE.init(...)`. The native engine is released when the screen leaves composition.

## Problems Hit

1. JNI class mismatch
`libtse_engine.so` exports `Java_com_example_tse_NativeTSE_*`, so binding Kotlin directly to `tw.com.johnnyhng.eztalk.asr.tse.NativeTSE` failed.

2. Missing native runtime dependencies
`libtse_engine.so` depends on `liboboe.so`, and `liboboe.so` depends on `libc++_shared.so`. Both libraries had to be packaged into `app/src/main/jniLibs/arm64-v8a/`.

3. Misleading error handling
Swallowing `UnsatisfiedLinkError` during `System.loadLibrary(...)` made failures show up later as missing JNI implementations. The loader now preserves the real native load failure.

4. JNI method name mismatch during wrapper refactor
Renaming native methods to names not exported by the `.so` broke symbol lookup. The JNI-bound class must keep `init`, `processFrame`, and `release`.

## Packaging Requirements

- `app/build.gradle.kts`
  - `buildFeatures.prefab = true`
  - `implementation("com.google.oboe:oboe:1.8.0")`
- `app/src/main/jniLibs/arm64-v8a/`
  - `libtse_engine.so`
  - `libonnxruntime.so`
  - `liboboe.so`
  - `libc++_shared.so`

## Current Constraint

The bundled TSE native stack is currently only packaged for `arm64-v8a`.
