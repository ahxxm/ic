import SwiftUI

enum CommitState {
    case saving
    case done(CommitResult)
    case error(String)
}

struct CommitView: View {
    let previews: [ImageCompressionPreview]
    var onFinish: () -> Void

    @State private var state: CommitState = .saving

    var body: some View {
        VStack(spacing: 16) {
            switch state {
            case .saving:
                Text("Saving compressed photos...").font(.headline)
                ProgressView().frame(maxWidth: 280)
                Text("Confirm the deletion prompt to reclaim space.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            case .done(let result):
                Text("Compression Complete").font(.title)
                Text("Saved \(formatBytes(result.savedBytes, signed: false))")
                    .font(.largeTitle)
                    .foregroundStyle(Color.accentColor)
                Text("across \(result.count) images")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Button("Done") { onFinish() }
                    .buttonStyle(.borderedProminent)
                    .padding(.top, 24)
            case .error(let message):
                Text("Error").font(.title).foregroundStyle(.red)
                Text(message).multilineTextAlignment(.center)
                Button("Back") { onFinish() }.buttonStyle(.borderedProminent)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationBarBackButtonHidden(true)
        .task {
            do {
                let result = try await Committer.commit(previews)
                state = .done(result)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
}
