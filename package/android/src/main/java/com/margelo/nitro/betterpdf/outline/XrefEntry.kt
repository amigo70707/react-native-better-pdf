package com.margelo.nitro.betterpdf.outline

/** Where an object lives (ISO 32000-1 §7.5.4, §7.5.8). */
sealed interface XrefEntry {
  /** A regular `n g obj` at a byte offset. */
  data class InFile(val offset: Long) : XrefEntry

  /** Object number [index] inside the object stream [streamNumber]. */
  data class Compressed(val streamNumber: Int, val index: Int) : XrefEntry
}
