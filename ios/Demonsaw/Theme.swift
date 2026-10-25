import SwiftUI

// Port of DemonsawColors from the Android Compose app.
enum DsTheme {
    static let blue = Color(hex: 0x0C9BDC)
    static let green = Color(hex: 0x52C175)
    static let background = Color(hex: 0x0F1419)
    static let surface = Color(hex: 0x1A2129)
    static let surfaceHigh = Color(hex: 0x232C36)
    static let onBackground = Color(hex: 0xE6EDF3)
    static let onSurfaceVariant = Color(hex: 0x9FB0C0)
    static let error = Color(hex: 0xE5534B)
    static let warn = Color(hex: 0xD9A441)
    static let warnOrange = Color(hex: 0xD97C41)
    static let mutedDot = Color(hex: 0x6B7B8C)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0
        )
    }
}

// Port of parseHex() - per-peer accent colors from the snapshot.
func dsParseHex(_ hex: String) -> Color {
    var value = hex.trimmingCharacters(in: .whitespacesAndNewlines)
    if value.isEmpty { value = "0c9bdc" }
    guard value.count >= 6, let rgb = UInt32(value.prefix(6), radix: 16) else { return DsTheme.blue }
    return Color(
        red: Double((rgb >> 16) & 0xFF) / 255.0,
        green: Double((rgb >> 8) & 0xFF) / 255.0,
        blue: Double(rgb & 0xFF) / 255.0
    )
}

// Port of formatSize()
func dsFormatSize(_ size: Int64) -> String {
    let units = ["B", "KB", "MB", "GB", "TB"]
    var value = Double(size)
    var unit = 0
    while value >= 1024 && unit < units.count - 1 {
        value /= 1024
        unit += 1
    }
    if unit == 0 { return "\(size) B" }
    return String(format: "%.1f %@", value, units[unit])
}

// Port of formatTime() / formatClock()
func dsFormatTime(_ time: Int64) -> String {
    guard time > 0 else { return "" }
    let date = Date(timeIntervalSince1970: TimeInterval(time))
    let formatter = DateFormatter()
    formatter.dateStyle = .short
    formatter.timeStyle = .short
    return formatter.string(from: date)
}

func dsFormatClock(_ time: Int64) -> String {
    guard time > 0 else { return "" }
    let date = Date(timeIntervalSince1970: TimeInterval(time))
    let formatter = DateFormatter()
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return formatter.string(from: date)
}
