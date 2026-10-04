import Foundation

/// PDF dates (ISO 32000-1 §7.9.4): `D:YYYYMMDDHHmmSSOHH'mm'`, every part
/// after the year optional (defaults: January, the 1st, midnight), `O` one
/// of `Z`, `+`, `-`. Without a time zone the date is read as UTC (as PDFKit
/// does). The same rules as Android's `PdfDate`; PDFKit's own parsing
/// (`documentAttributes`) also accepts malformed dates, which would make the
/// platforms disagree.
enum PdfDate {
  private static let format = try! NSRegularExpression(
    pattern: #"^(?:D:)?([0-9]{4})([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?(?:([Zz+-])(?:([0-9]{2})(?:'?([0-9]{2}))?'?)?)?$"#
  )

  private static let calendar: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar
  }()

  /// Milliseconds since the Unix epoch; nil for anything that is not a valid PDF date.
  static func parse(_ text: String) -> Double? {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let match = format.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) else {
      return nil
    }
    func string(_ index: Int) -> String? {
      Range(match.range(at: index), in: trimmed).map { String(trimmed[$0]) }
    }
    func part(_ index: Int, _ fallback: Int) -> Int {
      string(index).flatMap { Int($0) } ?? fallback
    }
    let year = part(1, 0)
    let month = part(2, 1)
    let day = part(3, 1)
    let hour = part(4, 0)
    let minute = part(5, 0)
    let second = part(6, 0)
    let offsetHours = part(8, 0)
    let offsetMinutes = part(9, 0)
    guard year >= 1, (1...12).contains(month), (1...31).contains(day), hour <= 23, minute <= 59, second <= 59,
          offsetHours <= 23, offsetMinutes <= 59 else { return nil }
    let components = DateComponents(year: year, month: month, day: day, hour: hour, minute: minute, second: second)
    // A day the month does not have.
    guard components.isValidDate(in: calendar), let date = calendar.date(from: components) else { return nil }
    let offsetMs = Double(offsetHours * 60 + offsetMinutes) * 60_000
    let asUtc = (date.timeIntervalSince1970 * 1000).rounded()
    switch string(7) {
    case "+": return asUtc - offsetMs
    case "-": return asUtc + offsetMs
    default: return asUtc
    }
  }
}
