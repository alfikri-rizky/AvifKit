# AVIF Native Library Integration Guide

This guide explains how to integrate actual AVIF encoding/decoding libraries to replace the placeholder implementations.

## Overview

The AvifKit library is fully implemented with a working API structure, but uses placeholder encoding/decoding. To enable actual AVIF support, you need to:

1. **Android**: Integrate `libavif` C++ library
2. **iOS**: Integrate `avif.swift` library

## Android Integration (libavif)

### Step 1: Download and Build libavif

```bash
# Clone libavif repository
cd shared/src/androidMain/cpp/
git clone https://github.com/AOMediaCodec/libavif.git
cd libavif

# Build libavif
mkdir build
cd build
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DAVIF_CODEC_AOM=ON \
  -DAVIF_LOCAL_AOM=ON \
  -DBUILD_SHARED_LIBS=OFF

make -j$(nproc)
```

### Step 2: Update CMakeLists.txt

Replace the content of `shared/src/androidMain/cpp/CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.18.1)
project("avif-android-wrapper")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add libavif
set(AVIF_CODEC_AOM ON)
set(AVIF_LOCAL_AOM ON)
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(libavif)

# Create JNI wrapper library
add_library(avif-android-wrapper shared
        avif_jni_wrapper.cpp
)

# Include directories
target_include_directories(avif-android-wrapper PRIVATE
        ${CMAKE_SOURCE_DIR}/libavif/include
)

# Link libraries
target_link_libraries(avif-android-wrapper
        avif
        android
        jnigraphics
        log
)
```

### Step 3: Update JNI Implementation

In `shared/src/androidMain/cpp/avif_jni_wrapper.cpp`, uncomment the actual libavif code:

#### For nativeEncode():

Replace the placeholder section with:

```cpp
#include "avif/avif.h"

JNIEXPORT jbyteArray JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeEncode(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray pixels,
    jint width,
    jint height,
    jint quality,
    jint speed,
    jint subsample) {

    jbyte* pixelData = env->GetByteArrayElements(pixels, nullptr);
    jsize pixelLength = env->GetArrayLength(pixels);

    // Create AVIF encoder
    avifEncoder* encoder = avifEncoderCreate();
    encoder->quality = quality;
    encoder->speed = speed;
    encoder->maxThreads = 4;

    // Create AVIF image
    avifPixelFormat pixelFormat;
    switch (subsample) {
        case 0: pixelFormat = AVIF_PIXEL_FORMAT_YUV444; break;
        case 1: pixelFormat = AVIF_PIXEL_FORMAT_YUV422; break;
        case 2: pixelFormat = AVIF_PIXEL_FORMAT_YUV420; break;
        default: pixelFormat = AVIF_PIXEL_FORMAT_YUV420; break;
    }

    avifImage* image = avifImageCreate(width, height, 8, pixelFormat);
    avifImageAllocatePlanes(image, AVIF_PLANES_YUV | AVIF_PLANES_A);

    // Convert RGBA to YUV
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.pixels = reinterpret_cast<uint8_t*>(pixelData);
    rgb.rowBytes = width * 4;
    rgb.format = AVIF_RGB_FORMAT_RGBA;

    avifResult convertResult = avifImageRGBToYUV(image, &rgb);
    if (convertResult != AVIF_RESULT_OK) {
        avifImageDestroy(image);
        avifEncoderDestroy(encoder);
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to convert RGB to YUV: %s", avifResultToString(convertResult));
        return nullptr;
    }

    // Encode the image
    avifRWData output = AVIF_DATA_EMPTY;
    avifResult encodeResult = avifEncoderWrite(encoder, image, &output);

    avifImageDestroy(image);
    avifEncoderDestroy(encoder);
    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    if (encodeResult != AVIF_RESULT_OK) {
        avifRWDataFree(&output);
        LOGE("Failed to encode: %s", avifResultToString(encodeResult));
        return nullptr;
    }

    // Create Java byte array
    jbyteArray result = env->NewByteArray(output.size);
    if (result) {
        env->SetByteArrayRegion(result, 0, output.size,
                               reinterpret_cast<const jbyte*>(output.data));
    }

    avifRWDataFree(&output);
    LOGI("Successfully encoded image: %dx%d", width, height);

    return result;
}
```

#### For nativeDecode():

Replace the placeholder section with:

```cpp
JNIEXPORT jobject JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeDecode(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray avifData) {

    jbyte* data = env->GetByteArrayElements(avifData, nullptr);
    jsize dataLength = env->GetArrayLength(avifData);

    // Create decoder
    avifDecoder* decoder = avifDecoderCreate();
    decoder->maxThreads = 4;

    // Parse AVIF data
    avifResult parseResult = avifDecoderSetIOMemory(
        decoder,
        reinterpret_cast<const uint8_t*>(data),
        dataLength
    );

    if (parseResult != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to parse AVIF: %s", avifResultToString(parseResult));
        return nullptr;
    }

    // Decode image
    avifResult decodeResult = avifDecoderNextImage(decoder);
    if (decodeResult != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to decode: %s", avifResultToString(decodeResult));
        return nullptr;
    }

    // Convert YUV to RGBA
    avifImage* image = decoder->image;
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = AVIF_RGB_FORMAT_RGBA;
    rgb.depth = 8;

    avifRGBImageAllocatePixels(&rgb);
    avifResult convertResult = avifImageYUVToRGB(image, &rgb);

    if (convertResult != AVIF_RESULT_OK) {
        avifRGBImageFreePixels(&rgb);
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to convert YUV to RGB: %s", avifResultToString(convertResult));
        return nullptr;
    }

    // Convert to Android Bitmap format (ARGB_8888)
    int width = rgb.width;
    int height = rgb.height;
    std::vector<int32_t> pixels(width * height);

    uint8_t* src = rgb.pixels;
    for (int i = 0; i < width * height; i++) {
        uint8_t r = src[i * 4 + 0];
        uint8_t g = src[i * 4 + 1];
        uint8_t b = src[i * 4 + 2];
        uint8_t a = src[i * 4 + 3];
        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    avifRGBImageFreePixels(&rgb);
    avifDecoderDestroy(decoder);
    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);

    // Create DecodedImage object
    jclass decodedImageClass = env->FindClass("com/alfikri/rizky/avifkit/DecodedImage");
    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([III)V");

    jintArray pixelArray = env->NewIntArray(pixels.size());
    env->SetIntArrayRegion(pixelArray, 0, pixels.size(), pixels.data());

    jobject result = env->NewObject(decodedImageClass, constructor,
                                    pixelArray, width, height);

    LOGI("Successfully decoded image: %dx%d", width, height);
    return result;
}
```

### Step 4: Build and Test

```bash
# Clean and rebuild
cd /path/to/AvifKit
./gradlew clean
./gradlew :shared:assembleDebug

# The native library will be built and included in the AAR
```

## iOS Integration (avif.swift)

### Step 1: Add avif.swift Dependency

#### Option A: CocoaPods

Add to your `Podfile`:

```ruby
target 'iosApp' do
  use_frameworks!
  pod 'avifswift', '~> 1.0'
end
```

Then run:
```bash
pod install
```

#### Option B: Swift Package Manager

Add to `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/awxkee/avif.swift.git", from: "1.0.0")
]
```

### Step 2: Create Swift Bridge

Create `shared/src/iosNative/AVIFNativeConverter.swift`:

```swift
import Foundation
import avif
import UIKit

@objc public class AVIFNativeConverter: NSObject {

    @objc public func encodeImage(
        _ image: UIImage,
        quality: Int,
        speed: Int
    ) -> Data? {
        do {
            let encoder = AVIFEncoder()
            encoder.quality = quality
            encoder.speed = speed

            return try encoder.encode(image: image)
        } catch {
            print("AVIF encoding error: \(error)")
            return nil
        }
    }

    @objc public func decodeAvif(_ avifData: Data) -> UIImage? {
        return AVIFDecoder.decode(avifData)
    }

    @objc public func isAvifFile(_ data: Data) -> Bool {
        guard data.count > 12 else { return false }
        let signature = data.subdata(in: 4..<12)
        let avifSignature = Data([0x66, 0x74, 0x79, 0x70, 0x61, 0x76, 0x69, 0x66])
        return signature == avifSignature
    }
}
```

### Step 3: Update iOS Implementation

In `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.ios.kt`:

Update the `encodeImageToAvif()` and `decodeAvifToImage()` methods to use the Swift bridge:

```kotlin
// This is pseudo-code showing the concept
// Actual implementation requires proper Swift/Kotlin interop

private fun encodeImageToAvif(image: UIImage, options: EncodingOptions): NSData {
    val nativeConverter = AVIFNativeConverter()

    return nativeConverter.encodeImage(
        image,
        quality = options.quality,
        speed = options.speed
    ) ?: throw AvifError.EncodingFailed("Native encoding failed")
}

private fun decodeAvifToImage(avifData: NSData): UIImage {
    val nativeConverter = AVIFNativeConverter()

    return nativeConverter.decodeAvif(avifData)
        ?: throw AvifError.DecodingFailed("Native decoding failed")
}
```

### Step 4: Configure Framework

Update `shared/build.gradle.kts` to export the Swift bridge:

```kotlin
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true

            // Export Swift interop
            export("avifswift:avifswift:1.0.0")
        }
    }
}
```

## Verification

### Test Android Integration

```kotlin
// Test on Android device or emulator
val converter = AvifConverter()
val result = converter.convertToFile(
    ImageInput.from("/path/to/test.jpg"),
    "/path/to/output.avif",
    Priority.BALANCED
)
println("Saved to: $result")

// Check if it's actual AVIF
val isAvif = converter.isAvifFile(ImageInput.from(result))
println("Is AVIF: $isAvif")  // Should print "true" with real libavif
```

### Test iOS Integration

```swift
// Test on iOS simulator or device
Task {
    let converter = AvifConverter()
    let result = try await converter.convertToFile(
        input: ImageInput.from(path: "/path/to/test.jpg"),
        outputPath: "/path/to/output.avif",
        priority: Priority.balanced,
        options: nil
    )
    print("Saved to: \(result)")

    // Check if it's actual AVIF
    let isAvif = converter.isAvifFile(input: ImageInput.from(path: result))
    print("Is AVIF: \(isAvif)")  // Should print "true" with real avif.swift
}
```

## Troubleshooting

### Android Issues

1. **Build fails with "libavif not found"**
   - Ensure libavif is properly cloned and built
   - Check CMakeLists.txt path to libavif directory
   - Try cleaning: `./gradlew clean`

2. **UnsatisfiedLinkError at runtime**
   - Check that native library is included in APK
   - Verify ABI filters match device architecture
   - Check LogCat for loading errors

3. **Encoding produces invalid AVIF**
   - Verify libavif built correctly
   - Check quality/speed parameters
   - Enable verbose logging in JNI code

### iOS Issues

1. **Module 'avif' not found**
   - Run `pod install` or SPM resolve
   - Check that avifswift is in dependencies
   - Clean build folder

2. **Swift/Kotlin interop errors**
   - Ensure @objc annotations are correct
   - Check framework export configuration
   - Verify bridging headers

3. **Encoding/decoding fails**
   - Check avif.swift version compatibility
   - Enable error logging
   - Verify image format is supported

## Performance Optimization

### Android

```cpp
// In avif_jni_wrapper.cpp
encoder->maxThreads = std::thread::hardware_concurrency();
encoder->speed = AVIF_SPEED_FASTEST;  // For real-time use
```

### iOS

```swift
// In AVIFNativeConverter.swift
encoder.maxThreads = ProcessInfo.processInfo.processorCount
encoder.speed = 10  // Fastest for real-time
```

## Next Steps

After integration:

1. **Run tests** - Test on real devices
2. **Benchmark** - Measure encoding/decoding performance
3. **Optimize** - Adjust quality/speed trade-offs
4. **Document** - Update README with actual performance metrics
5. **Release** - Publish to Maven Central / CocoaPods

## Resources

- [libavif GitHub](https://github.com/AOMediaCodec/libavif)
- [libavif Documentation](https://github.com/AOMediaCodec/libavif/blob/main/doc/avif.md)
- [avif.swift GitHub](https://github.com/awxkee/avif.swift)
- [AVIF Specification](https://aomediacodec.github.io/av1-avif/)

---

**Note**: This is a comprehensive guide. Actual integration may require adjustments based on your specific build environment and requirements.
