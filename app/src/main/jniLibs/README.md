# Native Library Install Guide

Native shared libraries (`*.so`) are intentionally not committed to this
repository. Download the native library release bundle from GitHub and install
the files into this directory before building the Android app.

## Directory Layout

For the current app build, install ARM64 libraries here:

```text
app/src/main/jniLibs/arm64-v8a/
```

The expected TSE runtime files are:

```text
app/src/main/jniLibs/arm64-v8a/libtse_engine.so
app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
```

Keep the exact filenames. Kotlin loads these libraries with:

```kotlin
System.loadLibrary("onnxruntime")
System.loadLibrary("tse_engine")
```

## Install From GitHub Release

1. Download the Android native library release bundle from the project GitHub
   release page.
2. Extract the bundle.
3. Copy the `arm64-v8a/*.so` files into:

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
- If another ABI is needed later, place its libraries under the matching Android
  ABI directory, such as `armeabi-v7a/`, `x86/`, or `x86_64/`.
- If the app fails with `UnsatisfiedLinkError`, verify that the required `.so`
  files exist in the ABI directory and that the filenames match exactly.
