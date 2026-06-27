import SwiftUI

@main
struct icApp: App {
    init() {
        cleanupOrphanedTempFiles()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

private func cleanupOrphanedTempFiles() {
    let tmp = FileManager.default.temporaryDirectory
    guard let files = try? FileManager.default.contentsOfDirectory(
        at: tmp, includingPropertiesForKeys: nil) else { return }
    for file in files where file.lastPathComponent.hasPrefix("compressed_") {
        try? FileManager.default.removeItem(at: file)
    }
}
