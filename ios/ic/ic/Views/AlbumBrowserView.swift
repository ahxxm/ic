import SwiftUI
import Photos

struct AlbumBrowserView: View {
    var onAlbumSelected: (String, String) -> Void

    @State private var authStatus: PHAuthorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    @State private var albums: [AlbumSummary] = []
    @State private var loading = false

    var body: some View {
        Group {
            switch authStatus {
            case .authorized, .limited:
                albumList
            case .denied, .restricted:
                permissionDenied
            default:
                permissionRequest
            }
        }
        .navigationTitle("Select Album")
        .task {
            if authStatus == .authorized || authStatus == .limited {
                await loadAlbums()
            }
        }
    }

    private var permissionRequest: some View {
        VStack(spacing: 16) {
            Text("Photo Library Access Required")
                .font(.headline)
            Text("Image Compressor needs access to your photos to compress JPEG images and save storage space.")
                .font(.body)
                .multilineTextAlignment(.center)
            Button("Grant Access") {
                Task {
                    authStatus = await PhotoLibrary.requestAuthorization()
                    if authStatus == .authorized || authStatus == .limited {
                        await loadAlbums()
                    }
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
    }

    private var permissionDenied: some View {
        VStack(spacing: 16) {
            Text("Photo Access Denied")
                .font(.headline)
            Text("Please enable photo access in Settings > Privacy > Photos.")
                .font(.body)
                .multilineTextAlignment(.center)
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
    }

    private var albumList: some View {
        List(albums) { album in
            Button {
                onAlbumSelected(album.id, album.name)
            } label: {
                HStack {
                    Text(album.name)
                        .font(.body)
                    Spacer()
                    Text("\(album.imageCount) images")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .tint(.primary)
        }
        .refreshable { await loadAlbums() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await loadAlbums() } } label: {
                    if loading {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .disabled(loading)
            }
        }
    }

    private func loadAlbums() async {
        guard !loading else { return }
        loading = true
        albums = await PhotoLibrary.getAlbums()
        loading = false
    }
}
