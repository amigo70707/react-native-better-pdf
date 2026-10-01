/** An image file written by `renderPage`. */
export interface PdfPageImage {
  /**
   * `file://` URI of the image in the app's cache directory; usable as an
   * `<Image source={{ uri }} />`. The file is yours: delete or move it when
   * done. The OS may delete cache files when storage runs low.
   */
  uri: string;
  /** Pixels. */
  width: number;
  /** Pixels. */
  height: number;
}
