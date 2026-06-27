import SwiftUI
import Photos

// Loads a PHAsset thumbnail/preview into a SwiftUI Image.
struct AssetImage: View {
    let asset: PHAsset
    var targetSize: CGSize = CGSize(width: 400, height: 400)
    var contentMode: SwiftUI.ContentMode = .fit

    @State private var uiImage: UIImage?

    var body: some View {
        Group {
            if let uiImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color(.secondarySystemBackground)
            }
        }
        .task(id: asset.localIdentifier) {
            uiImage = await load()
        }
    }

    private func load() async -> UIImage? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.isNetworkAccessAllowed = false
            // highQualityFormat delivers exactly one callback, so the
            // continuation resumes exactly once.
            options.deliveryMode = .highQualityFormat
            PHImageManager.default().requestImage(
                for: asset, targetSize: targetSize,
                contentMode: .aspectFit, options: options
            ) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }
}

// Loads a local file URL into a SwiftUI Image (for compressed temp previews).
struct FileImage: View {
    let url: URL
    @State private var uiImage: UIImage?

    var body: some View {
        Group {
            if let uiImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            } else {
                Color(.secondarySystemBackground)
            }
        }
        .task(id: url) {
            uiImage = UIImage(contentsOfFile: url.path)
        }
    }
}
