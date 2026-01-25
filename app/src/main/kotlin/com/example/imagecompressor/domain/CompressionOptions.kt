package com.example.imagecompressor.domain

enum class Encoder {
    MOZJPEG,
    JPEGLI
}

data class CompressionOptions(
    val quality: Int = 80,
    val preserveExif: Boolean = true,
    val convertPng: Boolean = false,
    val encoder: Encoder = Encoder.MOZJPEG
)
