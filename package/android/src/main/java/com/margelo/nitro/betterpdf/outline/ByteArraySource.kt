package com.margelo.nitro.betterpdf.outline

/** In-memory bytes: decoded object streams, and tests. */
class ByteArraySource(private val bytes: ByteArray) : PdfByteSource {
  override val size: Long = bytes.size.toLong()

  override fun read(position: Long, length: Int): ByteArray {
    if (position >= bytes.size) return ByteArray(0)
    val end = minOf(bytes.size.toLong(), position + length).toInt()
    return bytes.copyOfRange(position.toInt(), end)
  }
}
