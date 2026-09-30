package com.margelo.nitro.betterpdf.outline

/**
 * PDFDocEncoding (ISO 32000-1 Annex D.2): Latin-1 except for the ranges
 * 0x18–0x1F and 0x80–0xA0.
 */
internal object PdfDocEncoding {
  private const val UNDEFINED = '�'

  private val LOW = charArrayOf('˘', 'ˇ', 'ˆ', '˙', '˝', '˛', '˚', '˜')

  private val HIGH = charArrayOf(
    '•', '†', '‡', '…', '—', '–', 'ƒ', '⁄',
    '‹', '›', '−', '‰', '„', '“', '”', '‘',
    '’', '‚', '™', 'ﬁ', 'ﬂ', 'Ł', 'Œ', 'Š',
    'Ÿ', 'Ž', 'ı', 'ł', 'œ', 'š', 'ž', UNDEFINED,
    '€',
  )

  fun toChar(code: Int): Char = when (code) {
    in 0x18..0x1F -> LOW[code - 0x18]
    in 0x80..0xA0 -> HIGH[code - 0x80]
    0xAD -> UNDEFINED
    else -> code.toChar()
  }
}
