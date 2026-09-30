package com.margelo.nitro.betterpdf.outline

/** Random-access bytes of a PDF file. Implementations must allow positional reads. */
interface PdfByteSource {
  val size: Long

  /** Reads up to [length] bytes at [position]; fewer only at the end of the file. */
  fun read(position: Long, length: Int): ByteArray
}
