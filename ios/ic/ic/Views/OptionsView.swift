import SwiftUI

enum SampleState {
    case loading
    case ready(image: ImageItem, result: CompressionResult)
    case error(String)
}

@Observable
final class OptionsModel {
    var qualityLevel: QualityLevel = .good
    var preserveExif = true
    var convertPng = true
    var sampleState: SampleState = .loading
    var shouldDismiss = false

    private var sampleImage: ImageItem?
    private var currentTempFile: URL?
    private var debounceTask: Task<Void, Never>?

    func buildOptions() -> CompressionOptions {
        CompressionOptions(
            quality: qualityLevel.quality,
            preserveExif: preserveExif,
            convertPng: convertPng
        )
    }

    func loadSample(albumId: String) async {
        let images = (await PhotoLibrary.getImages(inAlbum: albumId))
            .filter { $0.isJpeg || (convertPng && $0.isPng) }
        guard let sample = images.randomElement() else {
            shouldDismiss = true
            return
        }
        sampleImage = sample
        await compress()
    }

    func setConvertPng(_ enabled: Bool, albumId: String) {
        guard convertPng != enabled else { return }
        convertPng = enabled
        sampleImage = nil
        Task { await loadSample(albumId: albumId) }
    }

    // Debounced recompression on option changes.
    func optionsChanged() {
        guard sampleImage != nil else { return }
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            await compress()
        }
    }

    private func compress() async {
        guard let sample = sampleImage else { return }
        sampleState = .loading
        if let old = currentTempFile { try? FileManager.default.removeItem(at: old) }

        do {
            let result = try await ImageCompressor.compress(image: sample, options: buildOptions())
            currentTempFile = result.tempFile
            sampleState = .ready(image: sample, result: result)
        } catch {
            currentTempFile = nil
            sampleState = .error(error.localizedDescription)
        }
    }

    func cleanup() {
        if let old = currentTempFile { try? FileManager.default.removeItem(at: old) }
    }
}

struct OptionsView: View {
    let albumId: String
    let albumName: String
    var onConfirm: (CompressionOptions) -> Void

    @State private var model = OptionsModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            sampleSection

            Divider()

            qualityRow
            Toggle("Keep EXIF", isOn: $model.preserveExif)
                .onChange(of: model.preserveExif) { model.optionsChanged() }
            Toggle("Include PNG", isOn: Binding(
                get: { model.convertPng },
                set: { model.setConvertPng($0, albumId: albumId) }
            ))

            Spacer()

            Text("Originals won't be modified yet")
                .font(.caption)
                .foregroundStyle(.secondary)

            Button {
                onConfirm(model.buildOptions())
            } label: {
                Text("Compress").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!isReady)
        }
        .padding()
        .navigationTitle(albumName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.loadSample(albumId: albumId) }
        .onDisappear { model.cleanup() }
        .onChange(of: model.shouldDismiss) { if model.shouldDismiss { dismiss() } }
    }

    private var isReady: Bool {
        if case .ready = model.sampleState { return true }
        return false
    }

    private var qualityRow: some View {
        HStack {
            Text("Quality")
            Spacer()
            Picker("Quality", selection: $model.qualityLevel) {
                ForEach(QualityLevel.allCases, id: \.self) { level in
                    Text("\(level.label) q\(level.quality)").tag(level)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: model.qualityLevel) { model.optionsChanged() }
        }
    }

    @ViewBuilder
    private var sampleSection: some View {
        switch model.sampleState {
        case .loading:
            VStack {
                ProgressView()
                Text("Compressing...").font(.caption).padding(.top, 8)
            }
            .frame(height: 200)
        case .ready(let image, let result):
            samplePreview(image: image, result: result)
        case .error(let message):
            Text(message)
                .foregroundStyle(.red)
                .frame(height: 200)
        }
    }

    private func samplePreview(image: ImageItem, result: CompressionResult) -> some View {
        let pct = Int(result.savingsPercent * 100)
        return VStack(spacing: 8) {
            AssetImage(asset: image.asset)
                .frame(height: 200)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            Text(image.outputName)
                .font(.subheadline)
                .lineLimit(1)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Text("\(formatBytes(result.originalSize, signed: false)) → \(formatBytes(result.compressedSize, signed: false))")
                Text("-\(pct)%")
                    .font(.headline)
                    .foregroundStyle(pct >= 10 ? Color.accentColor : .red)
            }
        }
    }
}
