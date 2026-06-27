import Foundation

func formatBytes(_ bytes: Int64, signed: Bool) -> String {
    let sign: String
    switch true {
    case bytes < 0: sign = "-"
    case signed && bytes > 0: sign = "+"
    default: sign = ""
    }
    let abs = Swift.abs(bytes)
    if abs < 1024 { return "\(sign)\(abs) B" }
    let kb = Double(abs) / 1024.0
    if kb < 1024 { return String(format: "%@%.1f KB", sign, kb) }
    let mb = kb / 1024.0
    if mb < 1024 { return String(format: "%@%.1f MB", sign, mb) }
    let gb = mb / 1024.0
    return String(format: "%@%.2f GB", sign, gb)
}
