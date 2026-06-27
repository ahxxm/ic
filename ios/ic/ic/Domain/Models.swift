import Photos

struct AlbumSummary: Identifiable {
    let id: String // PHAssetCollection.localIdentifier
    let name: String
    let imageCount: Int
}

struct ImageItem: Identifiable {
    let id: String // PHAsset.localIdentifier
    let asset: PHAsset
    let name: String
    let isJpeg: Bool
    let isPng: Bool

    // Output is always JPEG; a PNG input gets a .jpg name so the suffix
    // isn't misleading.
    var outputName: String {
        isPng ? (name as NSString).deletingPathExtension + ".jpg" : name
    }
}

struct CompressionResult {
    let originalSize: Int64
    let compressedSize: Int64
    let tempFile: URL

    var savingsBytes: Int64 { originalSize - compressedSize }
    var savingsPercent: Float {
        originalSize > 0 ? Float(savingsBytes) / Float(originalSize) : 0
    }
}

struct ImageCompressionPreview: Identifiable, Hashable {
    let id: String // same as ImageItem.id
    let image: ImageItem
    let originalSize: Int64
    let compressedSize: Int64
    let tempFile: URL

    var savingsBytes: Int64 { originalSize - compressedSize }
    var savingsPercent: Float {
        originalSize > 0 ? Float(savingsBytes) / Float(originalSize) : 0
    }

    static func == (lhs: ImageCompressionPreview, rhs: ImageCompressionPreview) -> Bool {
        lhs.id == rhs.id
    }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }

    static let minSavingsPercent: Float = 0.10
    static let minSavingsBytes: Int64 = 50_000

    static func shouldAutoDeselect(savingsPercent: Float, savingsBytes: Int64) -> Bool {
        savingsPercent < minSavingsPercent && savingsBytes < minSavingsBytes
    }
}
