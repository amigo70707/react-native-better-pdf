package com.margelo.nitro.betterpdf

/** Structural equality, so a re-sent but identical source never reloads. */
internal fun NativePdfSource?.isSameSource(other: NativePdfSource?): Boolean {
  if (this == null || other == null) return this == null && other == null
  return uri == other.uri && headers.orEmpty() == other.headers.orEmpty()
}
