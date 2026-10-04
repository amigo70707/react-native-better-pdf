import PDFKit

/// The form (AcroForm) of a displayed document: its fields' names in the
/// document's order and the pages with widgets, read from the file's
/// dictionaries without loading a page. Field values live in PDFKit's
/// widget annotations (`FormWidgetAnnotation`), which hold the reader's
/// changes. Main thread, like the document.
final class DocumentForm {
  private let document: PDFDocument
  /// Fully qualified names, in the order of the AcroForm's field tree.
  private let fieldOrder: [String: Int]
  private let widgetPages: [Int]

  init(document: PDFDocument) {
    self.document = document
    var order: [String: Int] = [:]
    for name in Self.fieldNames(of: document) where order[name] == nil {
      order[name] = order.count
    }
    fieldOrder = order
    widgetPages = Self.pagesWithWidgets(of: document)
  }

  /// Every field with its widgets, in the field tree's order (fields only
  /// PDFKit knows of after the others, in page order).
  func fields() -> [(name: String, widgets: [FormWidgetAnnotation])] {
    var byName: [String: [FormWidgetAnnotation]] = [:]
    var pageOrder: [String] = []
    for index in widgetPages {
      guard let page = document.page(at: index) else { continue }
      for case let widget as FormWidgetAnnotation in page.annotations {
        guard let name = widget.fieldName, !name.isEmpty else { continue }
        if byName[name] == nil { pageOrder.append(name) }
        byName[name, default: []].append(widget)
      }
    }
    let names = pageOrder.enumerated().sorted { a, b in
      (fieldOrder[a.element] ?? Int.max, a.offset) < (fieldOrder[b.element] ?? Int.max, b.offset)
    }.map(\.element)
    return names.map { ($0, byName[$0] ?? []) }
  }

  /// The widgets of the field named `name`.
  func widgets(named name: String) -> [FormWidgetAnnotation] {
    widgetPages.flatMap { index -> [FormWidgetAnnotation] in
      guard let page = document.page(at: index) else { return [] }
      return page.annotations.compactMap { $0 as? FormWidgetAnnotation }.filter { $0.fieldName == name }
    }
  }

  /// Pages whose `/Annots` hold a widget, from the page dictionaries.
  private static func pagesWithWidgets(of document: PDFDocument) -> [Int] {
    guard let file = document.documentRef else { return [] }
    return (0..<file.numberOfPages).filter { index in
      guard let dictionary = file.page(at: index + 1)?.dictionary else { return false }
      var annots: CGPDFArrayRef?
      guard CGPDFDictionaryGetArray(dictionary, "Annots", &annots), let annots else { return false }
      for position in 0..<CGPDFArrayGetCount(annots) {
        var annotation: CGPDFDictionaryRef?
        var subtype: UnsafePointer<CChar>?
        if CGPDFArrayGetDictionary(annots, position, &annotation), let annotation,
           CGPDFDictionaryGetName(annotation, "Subtype", &subtype), let subtype,
           String(cString: subtype) == "Widget" {
          return true
        }
      }
      return false
    }
  }

  /// Fully qualified field names, walking the AcroForm's `/Fields` tree.
  private static func fieldNames(of document: PDFDocument) -> [String] {
    guard let catalog = document.documentRef?.catalog else { return [] }
    var acroForm: CGPDFDictionaryRef?
    var fields: CGPDFArrayRef?
    guard CGPDFDictionaryGetDictionary(catalog, "AcroForm", &acroForm), let acroForm,
          CGPDFDictionaryGetArray(acroForm, "Fields", &fields), let fields else { return [] }
    var names: [String] = []
    func visit(_ array: CGPDFArrayRef, prefix: String?, depth: Int) {
      guard depth < 32 else { return }
      for position in 0..<CGPDFArrayGetCount(array) {
        var node: CGPDFDictionaryRef?
        guard CGPDFArrayGetDictionary(array, position, &node), let node else { continue }
        var partial: CGPDFStringRef?
        let part = CGPDFDictionaryGetString(node, "T", &partial)
          ? partial.flatMap { CGPDFStringCopyTextString($0) as String? } : nil
        // Widgets of a field have no name of their own.
        guard let part else { continue }
        let name = prefix.map { "\($0).\(part)" } ?? part
        names.append(name)
        var kids: CGPDFArrayRef?
        if CGPDFDictionaryGetArray(node, "Kids", &kids), let kids { visit(kids, prefix: name, depth: depth + 1) }
      }
    }
    visit(fields, prefix: nil, depth: 0)
    return names
  }
}
