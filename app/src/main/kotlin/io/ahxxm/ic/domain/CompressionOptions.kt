package io.ahxxm.ic.domain

enum class Encoder {
    MOZJPEG,
    JPEGLI
}

data class CompressionOptions(
    val quality: Int = 85,
    val preserveExif: Boolean = true,
    val convertPng: Boolean = false,
    val encoder: Encoder = Encoder.MOZJPEG
)
