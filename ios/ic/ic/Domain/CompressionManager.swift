import Foundation

enum BatchState {
    case compressing(current: Int, total: Int)
    case ready([ImageCompressionPreview], skipped: Int)
    case error(String)
}

@Observable
final class CompressionManager {
    var state: BatchState = .compressing(current: 0, total: 0)

    func compressAll(albumId: String, options: CompressionOptions) async {
        let allImages = await PhotoLibrary.getImages(inAlbum: albumId)
        let images = allImages.filter { $0.isJpeg || ($0.isPng && options.convertPng) }

        guard !images.isEmpty else {
            let hasPng = allImages.contains { $0.isPng }
            state = .error(
                hasPng && !options.convertPng
                    ? "No JPEG images found.\nEnable 'Include PNG' in options to include \(allImages.filter { $0.isPng }.count) PNG files."
                    : "No processable images in this album."
            )
            return
        }

        var previews: [ImageCompressionPreview] = []
        for (index, image) in images.enumerated() {
            state = .compressing(current: index + 1, total: images.count)
            // Skip an individual failure and keep going; one unreadable image
            // shouldn't abort the whole batch.
            guard let result = try? await ImageCompressor.compress(image: image, options: options)
            else { continue }
            previews.append(ImageCompressionPreview(
                id: image.id,
                image: image,
                originalSize: result.originalSize,
                compressedSize: result.compressedSize,
                tempFile: result.tempFile
            ))
        }

        guard !previews.isEmpty else {
            state = .error("Could not compress any of the \(images.count) images.")
            return
        }

        let skipped = images.count - previews.count
        previews.sort { $0.savingsBytes > $1.savingsBytes }
        state = .ready(previews, skipped: skipped)
    }

    static func cleanup(_ previews: [ImageCompressionPreview]) {
        for p in previews {
            try? FileManager.default.removeItem(at: p.tempFile)
        }
    }
}
