# Android Native Library Install Guide

Most native shared libraries (`*.so`) are intentionally not committed to this
repository. Download the Android native library release bundle from GitHub and
install the files into this directory before building the app.

## Install Location

Install ARM64 native libraries here:

```text
app/src/main/jniLibs/arm64-v8a/
```

The release bundle should provide these project native libraries:

```text
app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_GoogleTensor.so
app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
app/src/main/jniLibs/arm64-v8a/libtse_engine.so
```

Keep the exact filenames. Runtime code and native dependencies expect these
names:

```kotlin
System.loadLibrary("onnxruntime")
System.loadLibrary("sherpa-onnx-jni")
System.loadLibrary("tse_engine")
```

`libLiteRtDispatch_GoogleTensor.so` is checked by the Local Gemma NPU path.

## Install From GitHub Release

1. Download the Android native library release bundle from the project GitHub
   release page.
2. Extract the bundle.
3. Copy the release `arm64-v8a/*.so` files into:

   ```text
   app/src/main/jniLibs/arm64-v8a/
   ```

4. Build the app:

   ```sh
   ./gradlew compileDebugKotlin
   ```

## Notes

- `*.so` files are ignored by Git in this directory, so installing or replacing
  them will not show up in `git status`.
- Use ARM64 (`arm64-v8a`) libraries for normal device builds.
- Do not install old redundant copies such as `libonnxruntime.legacy.so`,
  `liboboe.so`, or `libc++_shared.so` unless a future native release explicitly
  requires them.
- LiteRT-LM libraries such as `liblitertlm_jni.so`, `libLiteRt.so`, and
  `libLiteRtClGlAccelerator.so` are provided by the Gradle dependency, not this
  manual release bundle.
- If another ABI is needed later, place its libraries under the matching Android
  ABI directory, such as `armeabi-v7a/`, `x86/`, or `x86_64/`.
- If the app fails with `UnsatisfiedLinkError`, verify that the required `.so`
  files exist in the ABI directory and that the filenames match exactly.
