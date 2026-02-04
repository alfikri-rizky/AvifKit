cmake_minimum_required(VERSION 3.22.1)
project("avif-android-wrapper")

# Android 15+ compatibility: 16 KB page size alignment
# CRITICAL: This MUST be set BEFORE any add_library() or add_subdirectory() calls
# Required for Google Play starting November 1st, 2025
# See: https://developer.android.com/16kb-page-size
if(ANDROID)
    # Set linker flags for 16 KB page alignment (16384 = 16 * 1024)
    # This ensures LOAD segments are aligned to 16 KB boundaries
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

    message(STATUS "✅ Enabled 16 KB page alignment for Android 15+ compatibility")
endif()

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build configuration
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Compiler optimization flags
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g")

# Check if libavif is available
set(LIBAVIF_DIR ${CMAKE_SOURCE_DIR}/libavif)
if(EXISTS ${LIBAVIF_DIR}/CMakeLists.txt)
    message(STATUS "✅ libavif found - Building with AVIF support")

    # Configure libavif build options
    set(AVIF_CODEC_AOM LOCAL CACHE STRING "Use AOM codec (build locally)" FORCE)
    set(AVIF_CODEC_AOM_ENCODE ON CACHE BOOL "Enable AOM encoding" FORCE)
    set(AVIF_CODEC_AOM_DECODE ON CACHE BOOL "Enable AOM decoding" FORCE)
    set(AVIF_LIBYUV OFF CACHE BOOL "Disable libyuv dependency")
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries")
    set(AVIF_BUILD_APPS OFF CACHE BOOL "Don't build apps")
    set(AVIF_BUILD_TESTS OFF CACHE BOOL "Don't build tests")
    set(AVIF_BUILD_EXAMPLES OFF CACHE BOOL "Don't build examples")

    # AOM-specific options for Android
    set(CONFIG_PIC 1 CACHE BOOL "Build position-independent code")
    set(ENABLE_CCACHE OFF CACHE BOOL "Disable ccache")
    set(ENABLE_DOCS OFF CACHE BOOL "Disable AOM docs")
    set(ENABLE_TESTS OFF CACHE BOOL "Disable AOM tests")
    set(ENABLE_TOOLS OFF CACHE BOOL "Disable AOM tools")
    set(ENABLE_EXAMPLES OFF CACHE BOOL "Disable AOM examples")

    # Add libavif subdirectory
    add_subdirectory(${LIBAVIF_DIR} ${CMAKE_BINARY_DIR}/libavif-build EXCLUDE_FROM_ALL)

    # Enable AVIF in wrapper
    add_compile_definitions(HAVE_LIBAVIF=1)

    set(HAS_LIBAVIF TRUE)
else()
    message(WARNING "⚠️  libavif not found - Using placeholder implementation")
    message(WARNING "⚠️  Run: ./scripts/setup-android-libavif.sh")
    add_compile_definitions(HAVE_LIBAVIF=0)

    set(HAS_LIBAVIF FALSE)
endif()

# Create JNI wrapper library
add_library(avif-android-wrapper SHARED
    avif_jni_wrapper.cpp
)

# Include directories
target_include_directories(avif-android-wrapper PRIVATE
    ${CMAKE_SOURCE_DIR}
)

if(HAS_LIBAVIF)
    target_include_directories(avif-android-wrapper PRIVATE
        ${LIBAVIF_DIR}/include
    )
endif()

# Link libraries
target_link_libraries(avif-android-wrapper
    android
    jnigraphics
    log
)

if(HAS_LIBAVIF)
    target_link_libraries(avif-android-wrapper avif)
endif()

# Android 15+ compatibility: Apply 16 KB page alignment to the target
# This MUST be done after target_link_libraries()
if(ANDROID)
    target_link_options(avif-android-wrapper PRIVATE
        "-Wl,-z,max-page-size=16384"
    )
    message(STATUS "✅ Applied 16 KB page alignment to avif-android-wrapper")
endif()

# Strip symbols in release builds to reduce binary size
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET avif-android-wrapper POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:avif-android-wrapper>
        COMMENT "Stripping symbols to reduce binary size"
    )
endif()

# Print build summary
message(STATUS "========================================")
message(STATUS "AvifKit Native Library Configuration")
message(STATUS "========================================")
message(STATUS "Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ Standard: ${CMAKE_CXX_STANDARD}")
if(HAS_LIBAVIF)
    message(STATUS "AVIF Support: ✅ ENABLED (using libavif)")
else()
    message(STATUS "AVIF Support: ⚠️  DISABLED (placeholder mode)")
endif()
message(STATUS "========================================")
