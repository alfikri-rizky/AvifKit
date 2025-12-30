# Changelog

All notable changes to AvifKit will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- WebAssembly (WASM) support
- Desktop platforms (JVM, Windows, Linux, macOS)
- Batch conversion API
- Progress callbacks
- Cancel support for long-running conversions

## [0.1.0] - 2025-01-15

### Added
- Initial beta release of AvifKit
- Android support (API 24+)
- iOS support (iOS 14.0+)
- AVIF encoding with libavif
- Compression strategies:
  - SMART: Binary search for optimal quality
  - STRICT: Exhaustive search for minimum size
- Quality presets (SPEED, BALANCED, QUALITY, STORAGE)
- Custom encoding parameters:
  - Quality control (0-100)
  - Encoding speed (0-10)
  - Chroma subsampling (YUV444/422/420)
  - Alpha quality control
  - Lossless encoding
  - Metadata preservation
  - Max dimension limiting
  - Target file size with adaptive compression
- Kotlin Multiplatform support
- Suspend function API with coroutines
- Comprehensive error handling
- iOS Swift integration guide

### Technical
- Built with Kotlin 2.2.20
- Kotlin Multiplatform Mobile (KMM)
- Coroutines for async operations
- Android: libavif via JNI
- iOS: Swift bridge to libavif
