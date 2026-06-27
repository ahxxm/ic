import Photos

struct CommitResult {
    let count: Int
    let savedBytes: Int64
}

enum CommitError: Error, LocalizedError {
    case noItems
    case changeFailed(String)

    var errorDescription: String? {
        switch self {
        case .noItems: "No images to save"
        case .changeFailed(let m): "Failed to save: \(m)"
        }
    }
}

// PhotoKit can't replace an existing asset's resource in place, so reclaiming
// space means creating new assets from the compressed files (preserving
// date/location) and deleting the originals. Both happen in one performChanges
// block so it is atomic: the system shows a single delete confirmation, and
// cancelling it rolls back the creations too (no orphaned duplicates).
struct Committer {
    static func commit(_ previews: [ImageCompressionPreview]) async throws -> CommitResult {
        guard !previews.isEmpty else { throw CommitError.noItems }

        let originals = previews.map { $0.image.asset }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                for preview in previews {
                    let resourceOptions = PHAssetResourceCreationOptions()
                    resourceOptions.originalFilename = preview.image.outputName
                    let request = PHAssetCreationRequest.forAsset()
                    request.addResource(with: .photo, fileURL: preview.tempFile, options: resourceOptions)
                    request.creationDate = preview.image.asset.creationDate
                    request.location = preview.image.asset.location
                }
                PHAssetChangeRequest.deleteAssets(originals as NSArray)
            }
        } catch {
            let ns = error as NSError
            throw CommitError.changeFailed("\(ns.localizedDescription) [\(ns.domain) \(ns.code)]")
        }

        let totalSaved = previews.reduce(Int64(0)) { $0 + $1.savingsBytes }
        CompressionManager.cleanup(previews)
        return CommitResult(count: previews.count, savedBytes: totalSaved)
    }
}
