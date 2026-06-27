enum QualityLevel: String, CaseIterable {
    case ok, good, great

    var label: String { rawValue }
    // mozjpeg quality values matching Android app
    var quality: Int {
        switch self {
        case .ok: 75
        case .good: 85
        case .great: 90
        }
    }
}

struct CompressionOptions: Hashable {
    let quality: Int
    let preserveExif: Bool
    let convertPng: Bool
}
