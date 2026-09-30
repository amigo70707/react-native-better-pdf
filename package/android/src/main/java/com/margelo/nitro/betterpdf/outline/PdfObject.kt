package com.margelo.nitro.betterpdf.outline

/**
 * The PDF object model (ISO 32000-1 §7.3), as far as the outline reader
 * needs it. A sealed family in one file: the variants are only meaningful
 * together.
 */
sealed interface PdfObject {
  data object Null : PdfObject

  data class Bool(val value: Boolean) : PdfObject

  data class Number(val value: Double) : PdfObject {
    val intValue: Int? get() = if (value == Math.floor(value) && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) value.toInt() else null
  }

  data class Name(val value: String) : PdfObject

  /** Raw bytes; decode text with [toText]. */
  class Str(val bytes: ByteArray) : PdfObject {
    override fun equals(other: Any?): Boolean = other is Str && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
  }

  data class Array(val items: List<PdfObject>) : PdfObject

  data class Dictionary(val entries: Map<String, PdfObject>) : PdfObject {
    operator fun get(key: String): PdfObject? = entries[key]
  }

  data class Reference(val number: Int, val generation: Int) : PdfObject

  /** A stream: its dictionary and where the undecoded data lives in the file. */
  data class Stream(val dictionary: Dictionary, val dataOffset: Long, val dataLength: Int) : PdfObject
}
