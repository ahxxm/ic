import Photos

struct PhotoLibrary {
    static func requestAuthorization() async -> PHAuthorizationStatus {
        await PHPhotoLibrary.requestAuthorization(for: .readWrite)
    }

    static func getAlbums() async -> [AlbumSummary] {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                var albums: [AlbumSummary] = []
                let fetchOptions = PHFetchOptions()
                fetchOptions.predicate = NSPredicate(
                    format: "mediaType == %d", PHAssetMediaType.image.rawValue
                )

                let collections: [(PHAssetCollectionType, PHAssetCollectionSubtype)] = [
                    (.smartAlbum, .smartAlbumUserLibrary),
                    (.smartAlbum, .smartAlbumScreenshots),
                    (.smartAlbum, .smartAlbumFavorites),
                    (.album, .any),
                ]

                for (type, subtype) in collections {
                    let result = PHAssetCollection.fetchAssetCollections(
                        with: type, subtype: subtype, options: nil
                    )
                    result.enumerateObjects { collection, _, _ in
                        let assets = PHAsset.fetchAssets(in: collection, options: fetchOptions)
                        guard assets.count > 0 else { return }
                        albums.append(AlbumSummary(
                            id: collection.localIdentifier,
                            name: collection.localizedTitle ?? "Unknown",
                            imageCount: assets.count
                        ))
                    }
                }

                // Deduplicate by id (smart albums can overlap)
                var seen = Set<String>()
                albums = albums.filter { seen.insert($0.id).inserted }
                albums.sort { $0.imageCount > $1.imageCount }
                continuation.resume(returning: albums)
            }
        }
    }

    static func getImages(inAlbum albumId: String) async -> [ImageItem] {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                guard let collection = PHAssetCollection.fetchAssetCollections(
                    withLocalIdentifiers: [albumId], options: nil
                ).firstObject else {
                    continuation.resume(returning: [])
                    return
                }

                let fetchOptions = PHFetchOptions()
                fetchOptions.predicate = NSPredicate(
                    format: "mediaType == %d", PHAssetMediaType.image.rawValue
                )
                let assets = PHAsset.fetchAssets(in: collection, options: fetchOptions)
                var images: [ImageItem] = []

                assets.enumerateObjects { asset, _, _ in
                    guard let primary = PHAssetResource.assetResources(for: asset).first
                    else { return }
                    let uti = primary.uniformTypeIdentifier
                    let isJpeg = uti == "public.jpeg"
                    let isPng = uti == "public.png"
                    guard isJpeg || isPng else { return }

                    images.append(ImageItem(
                        id: asset.localIdentifier,
                        asset: asset,
                        name: primary.originalFilename,
                        isJpeg: isJpeg,
                        isPng: isPng
                    ))
                }

                continuation.resume(returning: images)
            }
        }
    }
}
