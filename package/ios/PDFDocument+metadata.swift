import PDFKit

extension PDFDocument {
  /// The information dictionary, read as raw strings from `documentRef` —
  /// like Android's `PdfInfoReader`: values trimmed of whitespace and NUL
  /// padding, empty ones left out, dates parsed by `PdfDate`. (PDFKit's
  /// `documentAttributes` splits keywords into an array and parses
  /// malformed dates leniently.) Call on the thread that owns the document.
  func metadata() -> PdfDocumentMetadata {
    let info = documentRef?.info
    func text(_ key: String) -> String? {
      guard let info else { return nil }
      var value: CGPDFStringRef?
      guard CGPDFDictionaryGetString(info, key, &value), let value,
            let text = CGPDFStringCopyTextString(value) as String? else { return nil }
      let trimmed = text.trimmingCharacters(in: Self.metadataPadding)
      return trimmed.isEmpty ? nil : trimmed
    }
    return PdfDocumentMetadata(
      title: text("Title"),
      author: text("Author"),
      subject: text("Subject"),
      keywords: text("Keywords"),
      creator: text("Creator"),
      producer: text("Producer"),
      createdAtMs: text("CreationDate").flatMap(PdfDate.parse),
      modifiedAtMs: text("ModDate").flatMap(PdfDate.parse)
    )
  }

  private static let metadataPadding = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{0}"))
}
