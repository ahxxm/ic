package io.ahxxm.ic.domain

enum class Encoder {
    MOZJPEG,
    JPEGLI
}

data class CompressionOptions(
    val quality: Int = 85,
    val preserveExif: Boolean = true,
    val convertPng: Boolean = false,
    val encoder: Encoder = Encoder.MOZJPEG,
    val halveOversized: Boolean = true
) {
    companion object {
        // Sources above this pixel count are halved when `halveOversized` is on
        const val OVERSIZE_PIXELS = 20_000_000
    }
}
