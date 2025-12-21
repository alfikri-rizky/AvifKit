#include <jni.h>
#include <android/log.h>
#include <vector>
#include <memory>
#include <cstring>

// NOTE: This is a placeholder implementation
// To use actual AVIF encoding/decoding, you need to:
// 1. Download and build libavif: https://github.com/AOMediaCodec/libavif
// 2. Include the header: #include "avif/avif.h"
// 3. Link against libavif in CMakeLists.txt
// 4. Replace placeholder implementations with actual libavif calls

#define LOG_TAG "AvifJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Native encoding function
 *
 * PLACEHOLDER IMPLEMENTATION - Returns a mock AVIF header
 * Replace with actual libavif implementation for production use
 */
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

    LOGI("nativeEncode called: %dx%d, quality=%d, speed=%d, subsample=%d",
         width, height, quality, speed, subsample);

    // Get pixel data from Java
    jbyte* pixelData = env->GetByteArrayElements(pixels, nullptr);
    jsize pixelLength = env->GetArrayLength(pixels);

    if (!pixelData) {
        LOGE("Failed to get pixel data");
        return nullptr;
    }

    // PLACEHOLDER: Create a mock AVIF file header
    // In production, replace this with actual libavif encoding:
    /*
    avifEncoder* encoder = avifEncoderCreate();
    encoder->quality = quality;
    encoder->speed = speed;

    avifImage* image = avifImageCreate(width, height, 8,
        subsample == 0 ? AVIF_PIXEL_FORMAT_YUV444 :
        subsample == 1 ? AVIF_PIXEL_FORMAT_YUV422 :
        AVIF_PIXEL_FORMAT_YUV420);

    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.pixels = reinterpret_cast<uint8_t*>(pixelData);
    rgb.rowBytes = width * 4;
    rgb.format = AVIF_RGB_FORMAT_RGBA;

    avifImageRGBToYUV(image, &rgb);

    avifRWData output = AVIF_DATA_EMPTY;
    avifEncoderWrite(encoder, image, &output);

    jbyteArray result = env->NewByteArray(output.size);
    env->SetByteArrayRegion(result, 0, output.size,
                           reinterpret_cast<const jbyte*>(output.data));

    avifRWDataFree(&output);
    avifImageDestroy(image);
    avifEncoderDestroy(encoder);
    */

    // PLACEHOLDER: Create mock AVIF header
    std::vector<uint8_t> mockAvif = {
        0x00, 0x00, 0x00, 0x20,  // size
        0x66, 0x74, 0x79, 0x70,  // ftyp
        0x61, 0x76, 0x69, 0x66,  // avif
        0x00, 0x00, 0x00, 0x00,  // minor version
        0x61, 0x76, 0x69, 0x66,  // compatible brands
        0x6D, 0x69, 0x66, 0x31   // mif1
    };

    LOGW("PLACEHOLDER: Returning mock AVIF data. Integrate libavif for actual encoding.");

    // Clean up
    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    // Create Java byte array for result
    jbyteArray result = env->NewByteArray(mockAvif.size());
    if (result) {
        env->SetByteArrayRegion(result, 0, mockAvif.size(),
                               reinterpret_cast<const jbyte*>(mockAvif.data()));
    }

    return result;
}

/**
 * Native decoding function
 *
 * PLACEHOLDER IMPLEMENTATION - Returns a simple colored image
 * Replace with actual libavif implementation for production use
 */
JNIEXPORT jobject JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeDecode(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray avifData) {

    LOGI("nativeDecode called");

    // Get AVIF data from Java
    jbyte* data = env->GetByteArrayElements(avifData, nullptr);
    jsize dataLength = env->GetArrayLength(avifData);

    if (!data) {
        LOGE("Failed to get AVIF data");
        return nullptr;
    }

    // PLACEHOLDER: Create a simple test image
    // In production, replace this with actual libavif decoding:
    /*
    avifDecoder* decoder = avifDecoderCreate();
    decoder->maxThreads = 4;

    avifDecoderSetIOMemory(decoder,
                           reinterpret_cast<const uint8_t*>(data),
                           dataLength);

    avifDecoderNextImage(decoder);

    avifImage* image = decoder->image;
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = AVIF_RGB_FORMAT_RGBA;

    avifRGBImageAllocatePixels(&rgb);
    avifImageYUVToRGB(image, &rgb);

    // Convert to Android Bitmap format (ARGB_8888)
    std::vector<int32_t> pixels(rgb.width * rgb.height);
    uint8_t* src = rgb.pixels;
    for (int i = 0; i < rgb.width * rgb.height; i++) {
        pixels[i] = (src[i*4+3] << 24) | (src[i*4] << 16) |
                   (src[i*4+1] << 8) | src[i*4+2];
    }

    avifRGBImageFreePixels(&rgb);
    avifDecoderDestroy(decoder);
    */

    // PLACEHOLDER: Create a 100x100 red image
    int width = 100;
    int height = 100;
    std::vector<int32_t> pixels(width * height);

    for (int i = 0; i < width * height; i++) {
        // ARGB format: 0xAARRGGBB (red color)
        pixels[i] = 0xFFFF0000;
    }

    LOGW("PLACEHOLDER: Returning 100x100 red image. Integrate libavif for actual decoding.");

    // Clean up
    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);

    // Create DecodedImage object
    jclass decodedImageClass = env->FindClass("com/alfikri/rizky/avifkit/DecodedImage");
    if (!decodedImageClass) {
        LOGE("Failed to find DecodedImage class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([III)V");
    if (!constructor) {
        LOGE("Failed to find DecodedImage constructor");
        return nullptr;
    }

    // Create int array for pixels
    jintArray pixelArray = env->NewIntArray(pixels.size());
    env->SetIntArrayRegion(pixelArray, 0, pixels.size(), pixels.data());

    // Create and return DecodedImage object
    jobject result = env->NewObject(decodedImageClass, constructor,
                                    pixelArray, width, height);

    LOGI("Successfully decoded image: %dx%d", width, height);

    return result;
}

/**
 * Check if data is AVIF format
 */
JNIEXPORT jboolean JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeIsAvif(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray data) {

    if (!data) return JNI_FALSE;

    jsize length = env->GetArrayLength(data);
    if (length < 12) return JNI_FALSE;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);

    // Check AVIF file signature (ftypavif)
    bool isAvif = (bytes[4] == 0x66 && bytes[5] == 0x74 &&
                   bytes[6] == 0x79 && bytes[7] == 0x70 &&
                   bytes[8] == 0x61 && bytes[9] == 0x76 &&
                   bytes[10] == 0x69 && bytes[11] == 0x66);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return isAvif ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
