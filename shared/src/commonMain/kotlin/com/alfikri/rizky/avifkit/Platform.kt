package com.alfikri.rizky.avifkit

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform