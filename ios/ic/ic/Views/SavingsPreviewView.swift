import SwiftUI

struct SavingsPreviewView: View {
    let albumId: String
    let options: CompressionOptions
    var onConfirm: ([ImageCompressionPreview]) -> Void

    @State private var manager = CompressionManager()
    @State private var selections: [String: Bool] = [:]
    @State private var confirmed = false
    @State private var comparison: ImageCompressionPreview?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            switch manager.state {
            case .compressing(let current, let total):
                compressing(current: current, total: total)
            case .ready(let previews, let skipped):
                ready(previews, skipped: skipped)
            case .error(let message):
                errorView(message)
            }
        }
        .navigationTitle("Review Savings")
        .navigationBarTitleDisplayMode(.inline)
        .task { await manager.compressAll(albumId: albumId, options: options) }
        .onDisappear {
            if case .ready(let previews, _) = manager.state, !confirmed {
                CompressionManager.cleanup(previews)
            }
        }
        .onChange(of: previewsReady) { syncSelections() }
        .sheet(item: $comparison) { preview in
            NavigationStack { ComparisonView(preview: preview) }
        }
    }

    private var previewsReady: Bool {
        if case .ready = manager.state { return true }
        return false
    }

    // Seed default selections once results are ready (auto-deselect low-savings).
    private func syncSelections() {
        guard case .ready(let previews, _) = manager.state else { return }
        selections = Dictionary(uniqueKeysWithValues: previews.map {
            ($0.id, !ImageCompressionPreview.shouldAutoDeselect(
                savingsPercent: $0.savingsPercent, savingsBytes: $0.savingsBytes))
        })
    }

    private func compressing(current: Int, total: Int) -> some View {
        VStack(spacing: 16) {
            Text("Compressing...").font(.headline)
            ProgressView(value: total > 0 ? Double(current) / Double(total) : 0)
                .frame(maxWidth: 280)
            Text("\(current) / \(total)").font(.subheadline)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func ready(_ previews: [ImageCompressionPreview], skipped: Int) -> some View {
        let selected = previews.filter { selections[$0.id] == true }
        let sizeDelta = selected.reduce(Int64(0)) { $0 + ($1.compressedSize - $1.originalSize) }

        return VStack(spacing: 0) {
            Text("\(selected.count) of \(previews.count) selected | \(formatBytes(sizeDelta, signed: true))")
                .font(.headline)
                .foregroundStyle(sizeDelta <= 0 ? Color.accentColor : .red)
                .padding(.vertical, 8)

            if skipped > 0 {
                Text("\(skipped) skipped (could not read)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack {
                Button("All") { setAll(previews, true) }
                Button("None") { setAll(previews, false) }
                Button("Smart") { smartSelect(previews) }
            }
            .buttonStyle(.bordered)
            .padding(.bottom, 8)

            List(previews) { preview in
                previewRow(preview)
            }
            .listStyle(.plain)

            Text("Originals will be replaced")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.vertical, 8)

            Button {
                let chosen = previews.filter { selections[$0.id] == true }
                confirmed = true
                onConfirm(chosen)
            } label: {
                Text("Apply to \(selected.count) Images").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(selected.isEmpty)
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
    }

    private func previewRow(_ preview: ImageCompressionPreview) -> some View {
        let sizeDelta = preview.compressedSize - preview.originalSize
        let changePct = preview.originalSize > 0 ? Int(sizeDelta * 100 / preview.originalSize) : 0
        let lowSavings = ImageCompressionPreview.shouldAutoDeselect(
            savingsPercent: preview.savingsPercent, savingsBytes: preview.savingsBytes)
        let isSelected = selections[preview.id] == true

        return HStack(spacing: 12) {
            Button {
                selections[preview.id] = !isSelected
            } label: {
                Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                    .foregroundStyle(isSelected ? Color.accentColor : .secondary)
            }
            .buttonStyle(.plain)

            AssetImage(asset: preview.image.asset, targetSize: CGSize(width: 96, height: 96), contentMode: .fill)
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 4))

            VStack(alignment: .leading) {
                Text(preview.image.outputName)
                    .font(.subheadline)
                    .lineLimit(1)
                    .foregroundStyle(lowSavings ? .secondary : .primary)
                Text("\(preview.originalSize / 1024)KB → \(preview.compressedSize / 1024)KB")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text("\(formatBytes(sizeDelta, signed: true)) (\(changePct >= 0 ? "+" : "")\(changePct)%)")
                .font(.subheadline)
                .foregroundStyle(lowSavings ? .red : Color.accentColor)
        }
        .contentShape(Rectangle())
        .onTapGesture { comparison = preview }
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: 16) {
            Text("Error").font(.title).foregroundStyle(.red)
            Text(message).multilineTextAlignment(.center)
            Button("Back") { dismiss() }.buttonStyle(.borderedProminent)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func setAll(_ previews: [ImageCompressionPreview], _ value: Bool) {
        for p in previews { selections[p.id] = value }
    }

    private func smartSelect(_ previews: [ImageCompressionPreview]) {
        for p in previews {
            selections[p.id] = !ImageCompressionPreview.shouldAutoDeselect(
                savingsPercent: p.savingsPercent, savingsBytes: p.savingsBytes)
        }
    }
}
