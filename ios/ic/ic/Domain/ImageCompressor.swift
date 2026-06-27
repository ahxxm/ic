import Photos
import UIKit
import ImageIO
import UniformTypeIdentifiers
import mozjpeg

enum CompressionError: Error, LocalizedError {
    case cannotLoad
    case cannotDecode
    case encodeFailed(String)
    case writeFailed(String)

    var errorDescription: String? {
        switch self {
        case .cannotLoad: "Cannot load image"
        case .cannotDecode: "Cannot decode image"
        case .encodeFailed(let m): m
        case .writeFailed(let m): m
        }
    }
}

struct ImageCompressor {
    static func loadImageData(for asset: PHAsset) async -> Data? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            // Images are local; never download from iCloud. Fail fast if the
            // file isn't available rather than silently fetching over network.
            options.isNetworkAccessAllowed = false
            options.deliveryMode = .highQualityFormat
            PHImageManager.default().requestImageDataAndOrientation(
                for: asset, options: options
            ) { data, _, _, _ in
                continuation.resume(returning: data)
            }
        }
    }

    static func compress(
        image: ImageItem,
        options: CompressionOptions
    ) async throws -> CompressionResult {
        guard let sourceData = await loadImageData(for: image.asset) else {
            throw CompressionError.cannotLoad
        }
        guard let uiImage = UIImage(data: sourceData) else {
            throw CompressionError.cannotDecode
        }

        // mozjpeg quality is 0.0-1.0, where quality * 100 = JPEG quality level.
        let compressed: Data
        do {
            compressed = try uiImage.mozjpegRepresentation(quality: Float(options.quality) / 100.0)
        } catch {
            throw CompressionError.encodeFailed(error.localizedDescription)
        }

        let finalData = options.preserveExif
            ? copyMetadata(from: sourceData, to: compressed) ?? compressed
            : compressed

        let tempFile = FileManager.default.temporaryDirectory
            .appendingPathComponent("compressed_\(UUID().uuidString).jpg")
        do {
            try finalData.write(to: tempFile)
        } catch {
            throw CompressionError.writeFailed(error.localizedDescription)
        }

        return CompressionResult(
            originalSize: Int64(sourceData.count),
            compressedSize: Int64(finalData.count),
            tempFile: tempFile
        )
    }

    // mozjpeg output carries no metadata, so re-mux the original's EXIF/GPS/TIFF
    // into the freshly-encoded JPEG via ImageIO.
    private static func copyMetadata(from source: Data, to jpeg: Data) -> Data? {
        guard let srcSource = CGImageSourceCreateWithData(source as CFData, nil),
              let srcProps = CGImageSourceCopyPropertiesAtIndex(srcSource, 0, nil) as? [CFString: Any],
              let destSource = CGImageSourceCreateWithData(jpeg as CFData, nil)
        else { return nil }

        let output = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(
            output, UTType.jpeg.identifier as CFString, 1, nil
        ) else { return nil }

        CGImageDestinationAddImageFromSource(dest, destSource, 0, srcProps as CFDictionary)
        guard CGImageDestinationFinalize(dest) else { return nil }
        return output as Data
    }
}
