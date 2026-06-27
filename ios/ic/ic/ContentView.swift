import SwiftUI

enum Route: Hashable {
    case options(albumId: String, albumName: String)
    case savingsPreview(albumId: String, options: CompressionOptions)
    case commit([ImageCompressionPreview])
}

struct ContentView: View {
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            AlbumBrowserView { albumId, albumName in
                path.append(Route.options(albumId: albumId, albumName: albumName))
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .options(let albumId, let albumName):
                    OptionsView(albumId: albumId, albumName: albumName) { options in
                        path.append(Route.savingsPreview(albumId: albumId, options: options))
                    }
                case .savingsPreview(let albumId, let options):
                    SavingsPreviewView(albumId: albumId, options: options) { selected in
                        path.append(Route.commit(selected))
                    }
                case .commit(let previews):
                    CommitView(previews: previews) {
                        path = NavigationPath()
                    }
                }
            }
        }
    }
}
