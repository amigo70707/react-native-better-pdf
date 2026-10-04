package com.margelo.nitro.betterpdf.outline

/**
 * Reads the trailer's `/Info` dictionary. Values are trimmed of whitespace
 * and NUL padding, like on iOS; a document without the dictionary has no
 * metadata. XMP metadata streams are not read. Encrypted documents are
 * rejected by [PdfDocumentReader].
 */
class PdfInfoReader(private val document: PdfDocumentReader) {
  fun read(): PdfInfo {
    val info = document.resolve(document.trailer["Info"]) as? PdfObject.Dictionary ?: return PdfInfo()
    fun text(key: String): String? =
      (document.resolve(info[key]) as? PdfObject.Str)
        ?.toText()
        ?.trim { it.isWhitespace() || it == '\u0000' }
        ?.takeIf { it.isNotEmpty() }
    return PdfInfo(
      title = text("Title"),
      author = text("Author"),
      subject = text("Subject"),
      keywords = text("Keywords"),
      creator = text("Creator"),
      producer = text("Producer"),
      createdAtMs = text("CreationDate")?.let(PdfDate::parse),
      modifiedAtMs = text("ModDate")?.let(PdfDate::parse),
    )
  }
}
