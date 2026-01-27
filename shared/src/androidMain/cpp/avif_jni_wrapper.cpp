#include <jni.h>
#include <android/log.h>
#include <vector>
#include <memory>
#include <cstring>

// Conditional libavif inclusion
#if HAVE_LIBAVIF
#include "avif/avif.h"
#endif

#define LOG_TAG "AvifJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Native encoding function with libavif support
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

    LOGI("nativeEncode: %dx%d, quality=%d, speed=%d, subsample=%d",
         width, height, quality, speed, subsample);

    // Get pixel data from Java
    jbyte* pixelData = env->GetByteArrayElements(pixels, nullptr);
    jsize pixelLength = env->GetArrayLength(pixels);

    if (!pixelData) {
        LOGE("Failed to get pixel data");
        return nullptr;
    }

#if HAVE_LIBAVIF
    // ==========================================
    // PRODUCTION: Using libavif
    // ==========================================

    LOGI("Using libavif for encoding");

    // Check codec availability first
    const char* codecName = avifCodecName(AVIF_CODEC_CHOICE_AUTO, AVIF_CODEC_FLAG_CAN_ENCODE);
    if (codecName && codecName[0] != '\0') {
        LOGI("Available encoder codec: %s", codecName);
    } else {
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("No encoder codec available! AOM codec not found.");
        return nullptr;
    }

    // Create AVIF encoder
    avifEncoder* encoder = avifEncoderCreate();
    if (!encoder) {
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to create AVIF encoder");
        return nullptr;
    }

    // Set encoding parameters
    encoder->quality = quality;
    encoder->qualityAlpha = quality;  // Same quality for alpha
    encoder->speed = speed;
    encoder->maxThreads = 4;  // Use up to 4 threads
    encoder->codecChoice = AVIF_CODEC_CHOICE_AUTO;

    // Determine pixel format from subsample
    avifPixelFormat pixelFormat;
    switch (subsample) {
        case 0: pixelFormat = AVIF_PIXEL_FORMAT_YUV444; break;
        case 1: pixelFormat = AVIF_PIXEL_FORMAT_YUV422; break;
        case 2:
        default: pixelFormat = AVIF_PIXEL_FORMAT_YUV420; break;
    }

    // Create AVIF image
    avifImage* image = avifImageCreate(width, height, 8, pixelFormat);
    if (!image) {
        avifEncoderDestroy(encoder);
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to create AVIF image");
        return nullptr;
    }

    // Allocate image planes
    avifResult allocResult = avifImageAllocatePlanes(image, AVIF_PLANES_YUV | AVIF_PLANES_A);
    if (allocResult != AVIF_RESULT_OK) {
        avifImageDestroy(image);
        avifEncoderDestroy(encoder);
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to allocate image planes: %s", avifResultToString(allocResult));
        return nullptr;
    }

    // Setup RGB image for conversion
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.pixels = reinterpret_cast<uint8_t*>(pixelData);
    rgb.rowBytes = width * 4;  // RGBA = 4 bytes per pixel
    rgb.format = AVIF_RGB_FORMAT_RGBA;
    rgb.depth = 8;

    // Convert RGBA to YUV
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

    // Clean up encoder and image
    avifImageDestroy(image);
    avifEncoderDestroy(encoder);
    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    if (encodeResult != AVIF_RESULT_OK) {
        avifRWDataFree(&output);
        LOGE("Failed to encode AVIF: %s", avifResultToString(encodeResult));
        return nullptr;
    }

    // Check if output is empty (codec not available)
    if (output.size == 0 || output.data == nullptr) {
        avifRWDataFree(&output);
        LOGE("Encoder produced empty output! AOM codec may not be linked properly.");
        LOGE("output.size=%zu, output.data=%p", output.size, output.data);
        return nullptr;
    }

    LOGI("Successfully encoded AVIF: %dx%d, output size=%zu bytes",
         width, height, output.size);

    // Create Java byte array for result
    jbyteArray result = env->NewByteArray(output.size);
    if (!result) {
        avifRWDataFree(&output);
        LOGE("Failed to allocate Java byte array for encoded data");
        return nullptr;
    }

    env->SetByteArrayRegion(result, 0, output.size,
                           reinterpret_cast<const jbyte*>(output.data));

    avifRWDataFree(&output);

    return result;

#else
    // ==========================================
    // PLACEHOLDER: Mock AVIF implementation
    // ==========================================

    LOGW("PLACEHOLDER: libavif not available, returning mock AVIF header");

    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    // Create minimal AVIF file signature
    std::vector<uint8_t> mockAvif = {
        0x00, 0x00, 0x00, 0x20,  // box size
        0x66, 0x74, 0x79, 0x70,  // 'ftyp'
        0x61, 0x76, 0x69, 0x66,  // 'avif'
        0x00, 0x00, 0x00, 0x00,  // minor version
        0x61, 0x76, 0x69, 0x66,  // compatible brand 'avif'
        0x6D, 0x69, 0x66, 0x31,  // compatible brand 'mif1'
        0x6D, 0x69, 0x61, 0x66   // compatible brand 'miaf'
    };

    jbyteArray result = env->NewByteArray(mockAvif.size());
    if (result) {
        env->SetByteArrayRegion(result, 0, mockAvif.size(),
                               reinterpret_cast<const jbyte*>(mockAvif.data()));
    }

    return result;
#endif
}

/**
 * Native decoding function with libavif support
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

#if HAVE_LIBAVIF
    // ==========================================
    // PRODUCTION: Using libavif
    // ==========================================

    LOGI("Using libavif for decoding");

    // Check decoder codec availability
    const char* decoderCodecName = avifCodecName(AVIF_CODEC_CHOICE_AUTO, AVIF_CODEC_FLAG_CAN_DECODE);
    if (decoderCodecName && decoderCodecName[0] != '\0') {
        LOGI("Available decoder codec: %s", decoderCodecName);
    } else {
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("No decoder codec available! AOM decoder not found.");
        return nullptr;
    }

    // Log first few bytes of AVIF data for debugging
    LOGI("AVIF data first 16 bytes: %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x",
         (unsigned char)data[0], (unsigned char)data[1], (unsigned char)data[2], (unsigned char)data[3],
         (unsigned char)data[4], (unsigned char)data[5], (unsigned char)data[6], (unsigned char)data[7],
         (unsigned char)data[8], (unsigned char)data[9], (unsigned char)data[10], (unsigned char)data[11],
         (unsigned char)data[12], (unsigned char)data[13], (unsigned char)data[14], (unsigned char)data[15]);

    // Create decoder
    avifDecoder* decoder = avifDecoderCreate();
    if (!decoder) {
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to create AVIF decoder");
        return nullptr;
    }

    // Set decoder options
    decoder->maxThreads = 4;
    decoder->ignoreXMP = AVIF_TRUE;
    decoder->ignoreExif = AVIF_FALSE;  // IMPORTANT: Preserve EXIF for orientation data

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

    LOGI("Parse successful, calling avifDecoderParse...");

    // Parse the AVIF structure first
    parseResult = avifDecoderParse(decoder);
    if (parseResult != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed in avifDecoderParse: %s", avifResultToString(parseResult));
        LOGE("Decoder state - imageCount: %d, imageIndex: %d", decoder->imageCount, decoder->imageIndex);
        return nullptr;
    }

    LOGI("Parse successful - imageCount: %d, imageIndex: %d", decoder->imageCount, decoder->imageIndex);

    // Decode first image
    avifResult decodeResult = avifDecoderNextImage(decoder);
    if (decodeResult != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to decode AVIF: %s", avifResultToString(decodeResult));
        LOGE("After decode - imageCount: %d, imageIndex: %d", decoder->imageCount, decoder->imageIndex);
        return nullptr;
    }

    LOGI("Decode successful - image dimensions: %dx%d, depth: %d",
         decoder->image->width, decoder->image->height, decoder->image->depth);

    // Get decoded image
    avifImage* image = decoder->image;

    // Setup RGB conversion
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = AVIF_RGB_FORMAT_RGBA;
    rgb.depth = 8;

    // Allocate RGB buffer
    avifResult allocResult = avifRGBImageAllocatePixels(&rgb);
    if (allocResult != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to allocate RGB pixels: %s", avifResultToString(allocResult));
        return nullptr;
    }

    // Convert YUV to RGB
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

        // Pack as ARGB (Android Bitmap format)
        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Clean up
    avifRGBImageFreePixels(&rgb);
    avifDecoderDestroy(decoder);
    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);

    // Create DecodedImage object
    jclass decodedImageClass = env->FindClass("com/alfikri/rizky/avifkit/DecodedImage");
    if (!decodedImageClass) {
        LOGE("Failed to find DecodedImage class");
        // Check for pending JNI exceptions
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([III)V");
    if (!constructor) {
        LOGE("Failed to find DecodedImage constructor");
        // Check for pending JNI exceptions
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }

    // Create int array for pixels
    jintArray pixelArray = env->NewIntArray(pixels.size());
    if (!pixelArray) {
        LOGE("Failed to allocate pixel array");
        return nullptr;
    }

    env->SetIntArrayRegion(pixelArray, 0, pixels.size(), reinterpret_cast<const jint*>(pixels.data()));

    // Create and return DecodedImage object
    jobject result = env->NewObject(decodedImageClass, constructor,
                                    pixelArray, width, height);

    if (!result) {
        LOGE("Failed to create DecodedImage object");
        // Check for pending JNI exceptions
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }

    LOGI("Successfully decoded AVIF: %dx%d", width, height);

    return result;

#else
    // ==========================================
    // PLACEHOLDER: Mock decoding
    // ==========================================

    LOGW("PLACEHOLDER: libavif not available, returning test image");

    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);

    // Create a simple 100x100 colored test image
    int width = 100;
    int height = 100;
    std::vector<int32_t> pixels(width * height);

    // Create a gradient test pattern
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int r = (x * 255) / width;
            int g = (y * 255) / height;
            int b = 128;
            int a = 255;
            pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

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

    jintArray pixelArray = env->NewIntArray(pixels.size());
    if (!pixelArray) {
        LOGE("Failed to allocate pixel array");
        return nullptr;
    }

    env->SetIntArrayRegion(pixelArray, 0, pixels.size(), reinterpret_cast<const jint*>(pixels.data()));

    jobject result = env->NewObject(decodedImageClass, constructor,
                                    pixelArray, width, height);

    if (!result) {
        LOGE("Failed to create DecodedImage object");
        return nullptr;
    }

    LOGI("Returned placeholder test image: %dx%d", width, height);

    return result;
#endif
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

/**
 * Get library information (for debugging)
 */
JNIEXPORT jstring JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeGetVersion(
    JNIEnv* env,
    jobject /* this */) {

#if HAVE_LIBAVIF
    const char* version = avifVersion();
    std::string info = "libavif v";
    info += version;
    return env->NewStringUTF(info.c_str());
#else
    return env->NewStringUTF("Placeholder (libavif not integrated)");
#endif
}

} // extern "C"
