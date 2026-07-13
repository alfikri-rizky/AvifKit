#include <jni.h>
#include <android/log.h>
#include <unistd.h>
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

// Codec thread count from the actual online CPU count (was hardcoded 4), capped so small
// images don't pay coordination overhead that outweighs the gain.
static int recommendedThreadCount() {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    if (n < 1) n = 1;
    if (n > 8) n = 8;
    return static_cast<int>(n);
}

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
    jint alphaQuality,
    jint speed,
    jint subsample,
    jboolean lossless,
    jboolean hasAlpha) {

    LOGI("nativeEncode: %dx%d, quality=%d, alphaQuality=%d, speed=%d, subsample=%d, lossless=%d, hasAlpha=%d",
         width, height, quality, alphaQuality, speed, subsample, (int)lossless, (int)hasAlpha);

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

    // Set encoding parameters. For lossless, both channels must be
    // AVIF_QUALITY_LOSSLESS (100) — see also the identity matrix below.
    encoder->quality = lossless ? AVIF_QUALITY_LOSSLESS : quality;
    encoder->qualityAlpha = lossless ? AVIF_QUALITY_LOSSLESS : alphaQuality;
    encoder->speed = speed;
    encoder->maxThreads = recommendedThreadCount();
    encoder->codecChoice = AVIF_CODEC_CHOICE_AUTO;

    // Determine pixel format from subsample. True lossless requires identity
    // matrix coefficients, which libavif only supports with YUV444 — any chroma
    // subsampling is inherently lossy, so it overrides the requested subsample.
    avifPixelFormat pixelFormat;
    if (lossless) {
        pixelFormat = AVIF_PIXEL_FORMAT_YUV444;
    } else {
        switch (subsample) {
            case 0: pixelFormat = AVIF_PIXEL_FORMAT_YUV444; break;
            case 1: pixelFormat = AVIF_PIXEL_FORMAT_YUV422; break;
            case 2:
            default: pixelFormat = AVIF_PIXEL_FORMAT_YUV420; break;
        }
    }

    // Create AVIF image
    avifImage* image = avifImageCreate(width, height, 8, pixelFormat);
    if (!image) {
        avifEncoderDestroy(encoder);
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to create AVIF image");
        return nullptr;
    }
    if (lossless) {
        // Without identity coefficients the RGB→YUV transform rounds, and
        // quality=100 alone is NOT lossless. avifImageCreate defaults to full range.
        image->matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_IDENTITY;
    }

    // Allocate image planes. Opaque sources skip the alpha plane entirely (M6): no wasted
    // all-0xFF alpha OBU, smaller files, and no phantom alpha reported on decode.
    avifPlanesFlags planes = hasAlpha ? (avifPlanesFlags)(AVIF_PLANES_YUV | AVIF_PLANES_A)
                                      : (avifPlanesFlags)AVIF_PLANES_YUV;
    avifResult allocResult = avifImageAllocatePlanes(image, planes);
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
    // For opaque images, tell libavif to treat the RGBA buffer as opaque (ignore the A byte),
    // so RGB->YUV neither reads nor emits alpha.
    rgb.ignoreAlpha = hasAlpha ? AVIF_FALSE : AVIF_TRUE;

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
    // PLACEHOLDER build (no libavif): fail loudly.
    // ==========================================
    // No mock data — returning fabricated bytes here silently ships garbage to
    // consumers (see CODE_REVIEW.md H7). A null return surfaces in Kotlin as
    // AvifError.EncodingFailed.

    LOGE("libavif not compiled into this build (HAVE_LIBAVIF=0); encoding unavailable. "
         "Run scripts/setup-android-libavif.sh and rebuild.");

    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    return nullptr;
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

    // Log first few bytes of AVIF data for debugging. Guard the length: without it, a <16-byte
    // input causes an out-of-bounds heap read here (M7).
    if (dataLength >= 16) {
        LOGI("AVIF data first 16 bytes: %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x",
             (unsigned char)data[0], (unsigned char)data[1], (unsigned char)data[2], (unsigned char)data[3],
             (unsigned char)data[4], (unsigned char)data[5], (unsigned char)data[6], (unsigned char)data[7],
             (unsigned char)data[8], (unsigned char)data[9], (unsigned char)data[10], (unsigned char)data[11],
             (unsigned char)data[12], (unsigned char)data[13], (unsigned char)data[14], (unsigned char)data[15]);
    }

    // Create decoder
    avifDecoder* decoder = avifDecoderCreate();
    if (!decoder) {
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Failed to create AVIF decoder");
        return nullptr;
    }

    // Set decoder options
    decoder->maxThreads = recommendedThreadCount();
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

    // AVIF stores orientation as irot/imir transform properties; libavif reports them
    // but does not rotate the pixels. Hand them to Kotlin, which applies them via
    // RgbaTransform (must be read before avifDecoderDestroy below).
    int irotAngle = 0;
    int imirAxis = -1;
    if (image->transformFlags & AVIF_TRANSFORM_IROT) {
        irotAngle = image->irot.angle & 3;
    }
    if (image->transformFlags & AVIF_TRANSFORM_IMIR) {
        imirAxis = image->imir.axis & 1;
    }

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

    // Guard the pixel-buffer allocation: a large image can need ~1 GB, and an uncaught
    // std::bad_alloc escaping this JNI frame calls std::terminate (app abort) instead of a
    // catchable AvifError (M7).
    std::vector<int32_t> pixels;
    try {
        pixels.resize(static_cast<size_t>(width) * static_cast<size_t>(height));
    } catch (const std::bad_alloc&) {
        avifRGBImageFreePixels(&rgb);
        avifDecoderDestroy(decoder);
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        LOGE("Out of memory allocating %dx%d pixel buffer", width, height);
        return nullptr;
    }

    uint8_t* src = rgb.pixels;
    for (int i = 0; i < width * height; i++) {
        uint32_t r = src[i * 4 + 0];
        uint32_t g = src[i * 4 + 1];
        uint32_t b = src[i * 4 + 2];
        uint32_t a = src[i * 4 + 3];

        // Pack as ARGB (Android Bitmap format). Compute in uint32_t: (a << 24) on an
        // int-promoted uint8_t is signed-overflow UB when a >= 128 (M7).
        pixels[i] = static_cast<int32_t>((a << 24) | (r << 16) | (g << 8) | b);
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

    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([IIIII)V");
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
                                    pixelArray, width, height, irotAngle, imirAxis);

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
    // PLACEHOLDER build (no libavif): fail loudly.
    // ==========================================
    // No mock gradient image — see the encode placeholder above. A null return
    // surfaces in Kotlin as AvifError.DecodingFailed.

    LOGE("libavif not compiled into this build (HAVE_LIBAVIF=0); decoding unavailable. "
         "Run scripts/setup-android-libavif.sh and rebuild.");

    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);

    return nullptr;
#endif
}

/**
 * Parse-only AVIF metadata: returns an int[]{ width, height, hasAlpha(0|1), depth } or null.
 * avifDecoderParse() populates dimensions and the alphaPresent flag WITHOUT decoding pixels, so
 * this is cheap and works regardless of the platform's own AVIF support (used by getImageInfo).
 */
JNIEXPORT jintArray JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeGetAvifInfo(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray avifData) {

#if HAVE_LIBAVIF
    if (!avifData) return nullptr;
    jsize dataLength = env->GetArrayLength(avifData);
    jbyte* data = env->GetByteArrayElements(avifData, nullptr);
    if (!data) return nullptr;

    avifDecoder* decoder = avifDecoderCreate();
    if (!decoder) {
        env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
        return nullptr;
    }

    jintArray result = nullptr;
    if (avifDecoderSetIOMemory(decoder, reinterpret_cast<const uint8_t*>(data), dataLength) == AVIF_RESULT_OK &&
        avifDecoderParse(decoder) == AVIF_RESULT_OK) {
        const avifImage* image = decoder->image;
        // alphaPresent is valid after parse (image->alphaPlane isn't allocated until decode).
        jint info[4] = {
            static_cast<jint>(image->width),
            static_cast<jint>(image->height),
            decoder->alphaPresent ? 1 : 0,
            static_cast<jint>(image->depth),
        };
        result = env->NewIntArray(4);
        if (result) {
            env->SetIntArrayRegion(result, 0, 4, info);
        }
    }

    avifDecoderDestroy(decoder);
    env->ReleaseByteArrayElements(avifData, data, JNI_ABORT);
    return result;
#else
    return nullptr;
#endif
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
