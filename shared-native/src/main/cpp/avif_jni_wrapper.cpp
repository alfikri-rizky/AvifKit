#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <vector>
#include <memory>
#include <new>
#include <cstdint>
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


#if HAVE_LIBAVIF
namespace {

avifPixelFormat pixelFormatFor(int subsample, bool lossless) {
    // True lossless needs identity matrix coefficients, which libavif only supports with YUV444.
    if (lossless) return AVIF_PIXEL_FORMAT_YUV444;
    switch (subsample) {
        case 0: return AVIF_PIXEL_FORMAT_YUV444;
        case 1: return AVIF_PIXEL_FORMAT_YUV422;
        default: return AVIF_PIXEL_FORMAT_YUV420;
    }
}

// One in-flight animation encode. libavif encodes each frame inside avifEncoderAddImage(), so the
// only state that has to survive between JNI calls is the encoder itself plus the frame geometry
// every frame must match.
struct AnimEncoder {
    avifEncoder* encoder = nullptr;
    int width = 0;
    int height = 0;
    avifPixelFormat pixelFormat = AVIF_PIXEL_FORMAT_YUV420;
    bool hasAlpha = false;
    bool lossless = false;
    int frameCount = 0;
};

// avifDecoderSetIOMemory borrows the buffer rather than copying it, so the bytes have to outlive
// every avifDecoderNextImage() call — hence the owned copy here.
struct AnimDecoder {
    avifDecoder* decoder = nullptr;
    std::vector<uint8_t> data;
};

// Copies a Java byte[] metadata blob onto the avifImage. A libavif failure is logged and treated
// as "no metadata", because losing an Exif block is a far smaller harm than failing the conversion.
//
// The Kotlin side guarantees the Exif payload is one libavif can parse (EncodedMetadata rewrites
// and validates it). That matters: avifEncoderWrite() rejects a payload it cannot find a TIFF
// header in, so an unchecked blob would turn into a failed encode rather than a missing tag.
void attachBlob(JNIEnv* env,
                avifImage* image,
                jbyteArray source,
                const char* label,
                avifResult (*set)(avifImage*, const uint8_t*, size_t)) {
    if (source == nullptr) return;
    jsize length = env->GetArrayLength(source);
    if (length <= 0) return;
    jbyte* bytes = env->GetByteArrayElements(source, nullptr);
    if (!bytes) return;
    avifResult result = set(image, reinterpret_cast<const uint8_t*>(bytes), (size_t)length);
    env->ReleaseByteArrayElements(source, bytes, JNI_ABORT);
    if (result != AVIF_RESULT_OK) {
        LOGE("Failed to attach %s metadata: %s", label, avifResultToString(result));
    }
}

void setImageMetadata(JNIEnv* env, avifImage* image, jbyteArray exif, jbyteArray xmp) {
    attachBlob(env, image, exif, "Exif", avifImageSetMetadataExif);
    attachBlob(env, image, xmp, "XMP", avifImageSetMetadataXMP);
}

// Playback time in milliseconds. libavif gives a still image a nominal 1-tick duration at
// timescale 1, which would read as a full second — a still has no playback time, so callers pass
// frameCount and get 0 back for one.
uint64_t millisFrom(uint64_t durationInTimescales, uint64_t timescale, int frameCount) {
    if (timescale == 0 || frameCount <= 1) return 0;
    return (durationInTimescales * 1000ULL) / timescale;
}

} // namespace
#endif

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
    jboolean hasAlpha,
    jbyteArray exif,
    jbyteArray xmp) {

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

    // Exif/XMP carried over from the source (EncodingOptions.preserveMetadata). Both are null when
    // the caller asked for a stripped file, which is the default.
    setImageMetadata(env, image, exif, xmp);

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

    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([IIIIII)V");
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
                                    pixelArray, width, height, irotAngle, imirAxis, 0);

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
        // alphaPresent, imageCount and the track timing are all valid after parse — no pixel
        // decode is needed to answer "is this animated, and how long is it?".
        jint info[7] = {
            static_cast<jint>(image->width),
            static_cast<jint>(image->height),
            decoder->alphaPresent ? 1 : 0,
            static_cast<jint>(image->depth),
            static_cast<jint>(decoder->imageCount),
            static_cast<jint>(millisFrom(decoder->durationInTimescales, decoder->timescale, decoder->imageCount)),
            static_cast<jint>(decoder->repetitionCount),
        };
        result = env->NewIntArray(7);
        if (result) {
            env->SetIntArrayRegion(result, 0, 7, info);
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

// ==================================================================================
// Animated AVIF (image sequences)
// ==================================================================================
//
// Frame-at-a-time by design. The Kotlin side decodes an animated GIF frame by frame
// (GifDecoder) and pushes each one through nativeAnimEncoderAddFrame; libavif encodes
// inside that call, so no more than one frame's RGBA is ever alive. Handing the whole
// animation across JNI instead would be width*height*4*frameCount bytes — 92 MB for a
// 48-frame 800x600 GIF, an OOM on the low-end devices this library still supports.
//
// The jlong handle owns the encoder. Kotlin must call nativeAnimEncoderDestroy in a
// finally block; Finish alone does not free it.

/**
 * Starts an image-sequence encode. Returns an opaque handle, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimEncoderCreate(
    JNIEnv* /* env */,
    jobject /* this */,
    jint width,
    jint height,
    jint quality,
    jint alphaQuality,
    jint speed,
    jint subsample,
    jboolean lossless,
    jboolean hasAlpha,
    jint timescale,
    jint repetitionCount) {

#if HAVE_LIBAVIF
    if (width <= 0 || height <= 0 || timescale <= 0) {
        LOGE("nativeAnimEncoderCreate: invalid geometry %dx%d timescale=%d", width, height, timescale);
        return 0;
    }

    const char* codecName = avifCodecName(AVIF_CODEC_CHOICE_AUTO, AVIF_CODEC_FLAG_CAN_ENCODE);
    if (!codecName || codecName[0] == '\0') {
        LOGE("No encoder codec available! AOM codec not found.");
        return 0;
    }

    avifEncoder* encoder = avifEncoderCreate();
    if (!encoder) {
        LOGE("Failed to create AVIF encoder");
        return 0;
    }

    encoder->quality = lossless ? AVIF_QUALITY_LOSSLESS : quality;
    encoder->qualityAlpha = lossless ? AVIF_QUALITY_LOSSLESS : alphaQuality;
    encoder->speed = speed;
    encoder->maxThreads = recommendedThreadCount();
    encoder->codecChoice = AVIF_CODEC_CHOICE_AUTO;
    encoder->timescale = static_cast<uint64_t>(timescale);
    encoder->repetitionCount = repetitionCount;

    AnimEncoder* handle = new (std::nothrow) AnimEncoder();
    if (!handle) {
        avifEncoderDestroy(encoder);
        LOGE("Failed to allocate animation encoder handle");
        return 0;
    }
    handle->encoder = encoder;
    handle->width = width;
    handle->height = height;
    handle->pixelFormat = pixelFormatFor(subsample, lossless == JNI_TRUE);
    handle->hasAlpha = hasAlpha == JNI_TRUE;
    handle->lossless = lossless == JNI_TRUE;

    LOGI("nativeAnimEncoderCreate: %dx%d quality=%d speed=%d timescale=%d repeat=%d alpha=%d",
         width, height, quality, speed, timescale, repetitionCount, (int)hasAlpha);
    return reinterpret_cast<jlong>(handle);
#else
    LOGE("libavif not compiled into this build (HAVE_LIBAVIF=0); animation encoding unavailable.");
    return 0;
#endif
}

/**
 * Encodes one RGBA8888 frame into the sequence. `durationInTimescales` is the frame's
 * on-screen time in the encoder's timescale units.
 */
JNIEXPORT jboolean JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimEncoderAddFrame(
    JNIEnv* env,
    jobject /* this */,
    jlong handleValue,
    jbyteArray pixels,
    jint durationInTimescales) {

#if HAVE_LIBAVIF
    AnimEncoder* handle = reinterpret_cast<AnimEncoder*>(handleValue);
    if (!handle || !handle->encoder || !pixels) return JNI_FALSE;

    const jsize expected = static_cast<jsize>(handle->width) * handle->height * 4;
    if (env->GetArrayLength(pixels) != expected) {
        LOGE("nativeAnimEncoderAddFrame: got %d bytes, expected %d for %dx%d RGBA",
             env->GetArrayLength(pixels), expected, handle->width, handle->height);
        return JNI_FALSE;
    }

    jbyte* pixelData = env->GetByteArrayElements(pixels, nullptr);
    if (!pixelData) return JNI_FALSE;

    avifImage* image = avifImageCreate(handle->width, handle->height, 8, handle->pixelFormat);
    if (!image) {
        env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
        LOGE("Failed to create AVIF frame image");
        return JNI_FALSE;
    }
    if (handle->lossless) {
        image->matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_IDENTITY;
    }

    jboolean ok = JNI_FALSE;
    avifPlanesFlags planes = handle->hasAlpha ? (avifPlanesFlags)(AVIF_PLANES_YUV | AVIF_PLANES_A)
                                              : (avifPlanesFlags)AVIF_PLANES_YUV;
    avifResult result = avifImageAllocatePlanes(image, planes);
    if (result == AVIF_RESULT_OK) {
        avifRGBImage rgb;
        avifRGBImageSetDefaults(&rgb, image);
        rgb.pixels = reinterpret_cast<uint8_t*>(pixelData);
        rgb.rowBytes = handle->width * 4;
        rgb.format = AVIF_RGB_FORMAT_RGBA;
        rgb.depth = 8;
        // GIF transparency is binary, so its composited pixels are identical under straight and
        // premultiplied alpha. Declaring straight is what AVIF stores, so no conversion runs.
        rgb.alphaPremultiplied = AVIF_FALSE;
        rgb.ignoreAlpha = handle->hasAlpha ? AVIF_FALSE : AVIF_TRUE;

        result = avifImageRGBToYUV(image, &rgb);
        if (result == AVIF_RESULT_OK) {
            result = avifEncoderAddImage(handle->encoder,
                                         image,
                                         static_cast<uint64_t>(durationInTimescales > 0 ? durationInTimescales : 1),
                                         AVIF_ADD_IMAGE_FLAG_NONE);
            if (result == AVIF_RESULT_OK) {
                handle->frameCount++;
                ok = JNI_TRUE;
            }
        }
    }
    if (!ok) {
        LOGE("nativeAnimEncoderAddFrame failed at frame %d: %s",
             handle->frameCount, avifResultToString(result));
    }

    avifImageDestroy(image);
    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);
    return ok;
#else
    return JNI_FALSE;
#endif
}

/**
 * Closes the sequence and returns the finished AVIF bytes, or null on failure.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimEncoderFinish(
    JNIEnv* env,
    jobject /* this */,
    jlong handleValue) {

#if HAVE_LIBAVIF
    AnimEncoder* handle = reinterpret_cast<AnimEncoder*>(handleValue);
    if (!handle || !handle->encoder || handle->frameCount == 0) {
        LOGE("nativeAnimEncoderFinish: nothing to finish");
        return nullptr;
    }

    avifRWData output = AVIF_DATA_EMPTY;
    avifResult result = avifEncoderFinish(handle->encoder, &output);
    if (result != AVIF_RESULT_OK || output.size == 0 || output.data == nullptr) {
        avifRWDataFree(&output);
        LOGE("avifEncoderFinish failed: %s", avifResultToString(result));
        return nullptr;
    }

    jbyteArray bytes = env->NewByteArray(output.size);
    if (!bytes) {
        avifRWDataFree(&output);
        LOGE("Failed to allocate Java byte array for animated AVIF");
        return nullptr;
    }
    env->SetByteArrayRegion(bytes, 0, output.size, reinterpret_cast<const jbyte*>(output.data));
    LOGI("Animated AVIF encoded: %d frames, %zu bytes", handle->frameCount, output.size);
    avifRWDataFree(&output);
    return bytes;
#else
    return nullptr;
#endif
}

JNIEXPORT void JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimEncoderDestroy(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong handleValue) {

#if HAVE_LIBAVIF
    AnimEncoder* handle = reinterpret_cast<AnimEncoder*>(handleValue);
    if (!handle) return;
    if (handle->encoder) avifEncoderDestroy(handle->encoder);
    delete handle;
#endif
}

/**
 * Opens an AVIF for frame-by-frame reading. Returns an opaque handle, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimDecoderCreate(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray avifData) {

#if HAVE_LIBAVIF
    if (!avifData) return 0;
    const jsize length = env->GetArrayLength(avifData);
    if (length <= 0) return 0;

    AnimDecoder* handle = new (std::nothrow) AnimDecoder();
    if (!handle) return 0;
    try {
        handle->data.resize(static_cast<size_t>(length));
    } catch (const std::bad_alloc&) {
        delete handle;
        LOGE("Out of memory copying %d bytes of AVIF data", length);
        return 0;
    }
    env->GetByteArrayRegion(avifData, 0, length, reinterpret_cast<jbyte*>(handle->data.data()));

    handle->decoder = avifDecoderCreate();
    if (!handle->decoder) {
        delete handle;
        LOGE("Failed to create AVIF decoder");
        return 0;
    }
    handle->decoder->maxThreads = recommendedThreadCount();
    handle->decoder->ignoreXMP = AVIF_TRUE;
    handle->decoder->ignoreExif = AVIF_FALSE;

    avifResult result = avifDecoderSetIOMemory(handle->decoder, handle->data.data(), handle->data.size());
    if (result == AVIF_RESULT_OK) {
        result = avifDecoderParse(handle->decoder);
    }
    if (result != AVIF_RESULT_OK) {
        LOGE("nativeAnimDecoderCreate: %s", avifResultToString(result));
        avifDecoderDestroy(handle->decoder);
        delete handle;
        return 0;
    }

    LOGI("nativeAnimDecoderCreate: %dx%d, %d frame(s), sequence=%d",
         handle->decoder->image->width, handle->decoder->image->height,
         handle->decoder->imageCount, (int)handle->decoder->imageSequenceTrackPresent);
    return reinterpret_cast<jlong>(handle);
#else
    return 0;
#endif
}

/**
 * Decodes the next frame as a DecodedImage carrying its own duration, or null when the
 * sequence is exhausted.
 */
JNIEXPORT jobject JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimDecoderNextFrame(
    JNIEnv* env,
    jobject /* this */,
    jlong handleValue) {

#if HAVE_LIBAVIF
    AnimDecoder* handle = reinterpret_cast<AnimDecoder*>(handleValue);
    if (!handle || !handle->decoder) return nullptr;

    avifDecoder* decoder = handle->decoder;
    avifResult result = avifDecoderNextImage(decoder);
    if (result != AVIF_RESULT_OK) {
        // AVIF_RESULT_NO_IMAGES_REMAINING is the normal end of a sequence, not a failure.
        if (result != AVIF_RESULT_NO_IMAGES_REMAINING) {
            LOGE("avifDecoderNextImage failed: %s", avifResultToString(result));
        }
        return nullptr;
    }

    avifImage* image = decoder->image;
    int irotAngle = 0;
    int imirAxis = -1;
    if (image->transformFlags & AVIF_TRANSFORM_IROT) irotAngle = image->irot.angle & 3;
    if (image->transformFlags & AVIF_TRANSFORM_IMIR) imirAxis = image->imir.axis & 1;

    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = AVIF_RGB_FORMAT_RGBA;
    rgb.depth = 8;
    if (avifRGBImageAllocatePixels(&rgb) != AVIF_RESULT_OK) {
        LOGE("Failed to allocate RGB pixels for frame %d", decoder->imageIndex);
        return nullptr;
    }
    if (avifImageYUVToRGB(image, &rgb) != AVIF_RESULT_OK) {
        avifRGBImageFreePixels(&rgb);
        LOGE("Failed to convert frame %d to RGB", decoder->imageIndex);
        return nullptr;
    }

    const int width = rgb.width;
    const int height = rgb.height;
    std::vector<int32_t> pixels;
    try {
        pixels.resize(static_cast<size_t>(width) * static_cast<size_t>(height));
    } catch (const std::bad_alloc&) {
        avifRGBImageFreePixels(&rgb);
        LOGE("Out of memory allocating %dx%d frame buffer", width, height);
        return nullptr;
    }

    const uint8_t* src = rgb.pixels;
    for (int i = 0; i < width * height; i++) {
        uint32_t r = src[i * 4 + 0];
        uint32_t g = src[i * 4 + 1];
        uint32_t b = src[i * 4 + 2];
        uint32_t a = src[i * 4 + 3];
        pixels[i] = static_cast<int32_t>((a << 24) | (r << 16) | (g << 8) | b);
    }
    avifRGBImageFreePixels(&rgb);

    const jint durationMillis = static_cast<jint>(
        millisFrom(decoder->imageTiming.durationInTimescales,
                   decoder->imageTiming.timescale,
                   decoder->imageCount));

    jclass decodedImageClass = env->FindClass("com/alfikri/rizky/avifkit/DecodedImage");
    if (!decodedImageClass) {
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(decodedImageClass, "<init>", "([IIIIII)V");
    if (!constructor) {
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }

    jintArray pixelArray = env->NewIntArray(pixels.size());
    if (!pixelArray) return nullptr;
    env->SetIntArrayRegion(pixelArray, 0, pixels.size(), reinterpret_cast<const jint*>(pixels.data()));

    return env->NewObject(decodedImageClass, constructor,
                          pixelArray, width, height, irotAngle, imirAxis, durationMillis);
#else
    return nullptr;
#endif
}

JNIEXPORT void JNICALL
Java_com_alfikri_rizky_avifkit_AvifConverter_nativeAnimDecoderDestroy(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong handleValue) {

#if HAVE_LIBAVIF
    AnimDecoder* handle = reinterpret_cast<AnimDecoder*>(handleValue);
    if (!handle) return;
    if (handle->decoder) avifDecoderDestroy(handle->decoder);
    delete handle;
#endif
}

} // extern "C"
