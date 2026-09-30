/**
 * Delivered to `PdfView`'s `onLinkPress` when the user taps a link that
 * points outside the document (a URI link annotation).
 *
 * Links to other pages of the same document are followed natively and are
 * not reported. The viewer never opens URLs itself: the app decides what to
 * do, for example `Linking.openURL(event.url)` after validating the scheme.
 */
export interface PdfLinkPressEvent {
  /** The link target exactly as written in the document. */
  url: string;
}
