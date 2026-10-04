import PDFKit

extension FormWidgetAnnotation {
  /// The field's type; nil for a widget PDFKit does not know.
  var formFieldType: PdfFormFieldType? {
    switch widgetFieldType {
    case .text: return .text
    case .button:
      switch widgetControlType {
      case .radioButtonControl: return .radio
      case .checkBoxControl: return .checkbox
      default: return .pushButton
      }
    case .choice: return isListChoice ? .listBox : .comboBox
    case .signature: return .signature
    default: return nil
    }
  }

  /// `/Ff`, which PDFKit copies onto widgets from their field.
  var fieldFlags: Int {
    (value(forAnnotationKey: .widgetFieldFlags) as? NSNumber)?.intValue ?? 0
  }

  /// Where the widget is shown, in `onPageTap`'s coordinates.
  func formWidget(pageIndex: Int, box: PDFDisplayBox) -> PdfFormWidget {
    let rect = page?.topLeftRect(from: bounds, box: box) ?? .zero
    return PdfFormWidget(
      page: Double(pageIndex), x: rect.minX, y: rect.minY, width: rect.width, height: rect.height
    )
  }
}

enum FormFieldFlag {
  static let required = 1 << 1
  static let edit = 1 << 18
  static let multiSelect = 1 << 21
}

extension NativePdfFormField {
  /// A field as the view reports it: its type and value from its first
  /// widget, the selection of a radio group from all of them.
  init?(name: String, widgets: [FormWidgetAnnotation], document: PDFDocument, box: PDFDisplayBox) {
    guard let first = widgets.first, let type = first.formFieldType else { return nil }
    let flags = first.fieldFlags
    // Line breaks as \n: files store \r\n or \r too.
    let text = (first.widgetStringValue ?? "").replacingOccurrences(of: "\r\n", with: "\n")
      .replacingOccurrences(of: "\r", with: "\n")
    var options: [String] = []
    var selected: [String] = []
    switch type {
    case .radio:
      options = widgets.map { $0.buttonWidgetStateString }
      selected = widgets.filter { $0.buttonWidgetState == .onState }.prefix(1).map { $0.buttonWidgetStateString }
    case .comboBox:
      options = first.choices ?? []
      selected = text.isEmpty ? [] : [text]
    case .listBox:
      options = first.choices ?? []
      selected = text.isEmpty ? [] : [text]
    default:
      break
    }
    self.init(
      name: name,
      type: type,
      isReadOnly: widgets.contains { $0.isReadOnlyInDocument },
      isRequired: flags & FormFieldFlag.required != 0,
      widgets: widgets.compactMap { widget in
        guard let page = widget.page else { return nil }
        return widget.formWidget(pageIndex: document.index(for: page), box: box)
      },
      text: type == .text || type == .comboBox ? text : "",
      isChecked: type == .checkbox && first.buttonWidgetState == .onState,
      options: options,
      selectedOptions: selected,
      isMultiline: type == .text && first.isMultiline,
      maxLength: type == .text ? Double(max(first.maximumLength, 0)) : 0,
      isMultiSelect: type == .listBox && flags & FormFieldFlag.multiSelect != 0,
      isEditable: type == .comboBox && flags & FormFieldFlag.edit != 0,
      isSigned: type == .signature && first.value(forAnnotationKey: .widgetValue) != nil
    )
  }
}
