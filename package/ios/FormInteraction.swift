import PDFKit
import UIKit

/// Whether the reader is changing the form now. PDFKit reports nothing
/// itself, and sets widget values while it loads pages too (measured: a
/// radio group's state, every time its page loads), so a value set counts
/// as the reader's only while they hit a widget — PDFKit sets a button's
/// state between `PDFViewAnnotationWillHit` and `PDFViewAnnotationHit` —
/// or edit one: text and choices are set while PDFKit's text field or
/// view in the PDF view is editing, before it ends.
final class FormInteraction {
  var isActive: Bool { isHitting || isEditing }
  private var isHitting = false
  private var isEditing = false
  private var observers: [NSObjectProtocol] = []

  init(pdfView: PDFView) {
    let center = NotificationCenter.default
    // `queue: nil`: delivered as posted, before PDFKit sets the value.
    observers.append(center.addObserver(forName: .PDFViewAnnotationWillHit, object: pdfView, queue: nil) { [weak self] _ in
      self?.isHitting = true
      // In case no `PDFViewAnnotationHit` follows.
      DispatchQueue.main.async { self?.isHitting = false }
    })
    observers.append(center.addObserver(forName: .PDFViewAnnotationHit, object: pdfView, queue: nil) { [weak self] _ in
      self?.isHitting = false
    })
    let began: [Notification.Name] = [UITextField.textDidBeginEditingNotification, UITextView.textDidBeginEditingNotification]
    let ended: [Notification.Name] = [UITextField.textDidEndEditingNotification, UITextView.textDidEndEditingNotification]
    for name in began {
      observers.append(center.addObserver(forName: name, object: nil, queue: nil) { [weak self, weak pdfView] note in
        guard let pdfView, (note.object as? UIView)?.isDescendant(of: pdfView) == true else { return }
        self?.isEditing = true
      })
    }
    for name in ended {
      observers.append(center.addObserver(forName: name, object: nil, queue: nil) { [weak self, weak pdfView] note in
        guard let pdfView, (note.object as? UIView)?.isDescendant(of: pdfView) == true else { return }
        self?.isEditing = false
      })
    }
  }

  deinit {
    observers.forEach(NotificationCenter.default.removeObserver)
  }
}
