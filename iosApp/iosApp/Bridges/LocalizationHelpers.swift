import Foundation

/// Russian plural forms: pluralRu(n, "день", "дня", "дней").
func pluralRu(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
    let mod10 = n % 10
    let mod100 = n % 100
    if (11...14).contains(mod100) { return many }
    if mod10 == 1 { return one }
    if (2...4).contains(mod10) { return few }
    return many
}

/// Language code → flag emoji (uses regional indicator code points).
func languageFlag(_ code: String) -> String {
    let mapping: [String: String] = [
        "ru": "RU", "en": "GB", "de": "DE", "es": "ES", "fr": "FR",
        "it": "IT", "ja": "JP", "zh": "CN", "pt": "PT", "tr": "TR"
    ]
    guard let country = mapping[code.lowercased()] else { return code.uppercased() }
    let base: UInt32 = 0x1F1E6 - 65
    var s = ""
    for ch in country.unicodeScalars {
        if let scalar = Unicode.Scalar(base + ch.value) {
            s.unicodeScalars.append(scalar)
        }
    }
    return s
}

/// Russian relative time: "сегодня" / "вчера" / "3 дня назад" / "2 недели назад".
func relativeTimeRu(_ epochMs: Int64) -> String {
    let then = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
    let cal = Calendar.current
    let thenDay = cal.startOfDay(for: then)
    let today = cal.startOfDay(for: Date())
    let days = cal.dateComponents([.day], from: thenDay, to: today).day ?? 0
    switch days {
    case ..<1: return "сегодня"
    case 1: return "вчера"
    case 2..<7: return "\(days) \(pluralRu(days, "день", "дня", "дней")) назад"
    case 7..<30:
        let w = days / 7
        return "\(w) \(pluralRu(w, "неделю", "недели", "недель")) назад"
    default:
        let m = days / 30
        return "\(m) \(pluralRu(m, "месяц", "месяца", "месяцев")) назад"
    }
}

/// Russian SRS forecast: "завтра" / "через 3 дня" / "через 2 недели".
func humanizeIntervalRu(_ days: Double) -> String {
    let d = max(1, Int(days))
    switch d {
    case 1: return "завтра"
    case 2..<7: return "через \(d) \(pluralRu(d, "день", "дня", "дней"))"
    case 7..<30:
        let w = d / 7
        return "через \(w) \(pluralRu(w, "неделю", "недели", "недель"))"
    default:
        let m = d / 30
        return "через \(m) \(pluralRu(m, "месяц", "месяца", "месяцев"))"
    }
}
