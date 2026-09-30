package com.margelo.nitro.betterpdf.outline

/**
 * Decodes a PDF text string (ISO 32000-1 §7.9.2.2): UTF-16BE with a BOM,
 * UTF-8 with a BOM (PDF 2.0), otherwise PDFDocEncoding.
 */
internal fun PdfObject.Str.toText(): String {
  val b = bytes
  if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) {
    return String(b, 2, b.size - 2, Charsets.UTF_16BE)
  }
  if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
    return String(b, 3, b.size - 3, Charsets.UTF_8)
  }
  val builder = StringBuilder(b.size)
  for (byte in b) builder.append(PdfDocEncoding.toChar(byte.toInt() and 0xff))
  return builder.toString()
}
