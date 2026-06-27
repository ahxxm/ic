import SwiftUI

struct ComparisonView: View {
    let preview: ImageCompressionPreview
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $tab) {
                Text("Original (\(formatBytes(preview.originalSize, signed: false)))").tag(0)
                Text("Compressed (\(formatBytes(preview.compressedSize, signed: false)))").tag(1)
            }
            .pickerStyle(.segmented)
            .padding()

            TabView(selection: $tab) {
                AssetImage(asset: preview.image.asset)
                    .padding(8)
                    .tag(0)
                FileImage(url: preview.tempFile)
                    .padding(8)
                    .tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            HStack {
                Text("Savings:").font(.headline)
                Spacer()
                Text("\(formatBytes(preview.savingsBytes, signed: false)) (\(Int(preview.savingsPercent * 100))%)")
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
            }
            .padding()
        }
        .navigationTitle(preview.image.outputName)
        .navigationBarTitleDisplayMode(.inline)
    }
}
