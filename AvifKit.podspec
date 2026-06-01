Pod::Spec.new do |spec|
  spec.name         = "AvifKit"
  spec.version      = "0.3.0"
  spec.summary      = "Kotlin Multiplatform AVIF converter library for iOS and Android"
  spec.description  = <<-DESC
    AvifKit is a comprehensive Kotlin Multiplatform library for converting images
    to AVIF format. It provides a unified API across iOS and Android with
    native performance. The AVIF codec (libavif + aom) is linked directly into
    the Kotlin/Native framework via cinterop, so the XCFramework is fully
    self-contained — no Swift bridge or extra codec dependency required.

    Features:
    - AVIF encoding with adaptive compression
    - Compression strategies (SMART & STRICT)
    - Priority presets for common use cases
    - Multi-threaded processing
    - Supports iOS and Android platforms
  DESC

  spec.homepage     = "https://github.com/alfikri-rizky/AvifKit"
  spec.license      = { :type => "MIT", :file => "LICENSE" }
  spec.author       = { "Rizky Alfikri" => "rizkyalfikri@gmail.com" }

  spec.ios.deployment_target = "15.0"
  spec.swift_version = "5.0"

  spec.source       = {
    :git => "https://github.com/alfikri-rizky/AvifKit.git",
    :tag => "#{spec.version}"
  }

  # Download pre-built Kotlin Multiplatform XCFramework from GitHub Release.
  # This XCFramework contains the shared Kotlin code (compiled for all iOS
  # architectures) AND the statically linked AVIF codec — it is self-contained.
  spec.vendored_frameworks = "Shared.xcframework"

  # Prepare command downloads the XCFramework from GitHub Release
  spec.prepare_command = <<-CMD
    curl -L -o Shared.xcframework.zip \
      https://github.com/alfikri-rizky/AvifKit/releases/download/#{spec.version}/Shared.xcframework.zip
    unzip -q Shared.xcframework.zip
    rm Shared.xcframework.zip
  CMD

  # Framework configuration
  spec.pod_target_xcconfig = {
    'SWIFT_VERSION' => '5.0',
    'OTHER_LDFLAGS' => '-framework UIKit -framework Foundation',
    'VALID_ARCHS' => 'arm64 x86_64',
    'HEADER_SEARCH_PATHS' => '$(inherited)',
    'IPHONEOS_DEPLOYMENT_TARGET' => '15.0'
  }

  # User target xcconfig
  spec.user_target_xcconfig = {
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386',
    'IPHONEOS_DEPLOYMENT_TARGET' => '15.0'
  }

  # Documentation
  spec.documentation_url = "https://github.com/alfikri-rizky/AvifKit/blob/main/README.md"
end
