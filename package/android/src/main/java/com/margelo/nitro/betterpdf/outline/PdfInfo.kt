package com.margelo.nitro.betterpdf.outline

/**
 * The document information dictionary (ISO 32000-1 §14.3.3). Entries that
 * are absent, empty or not text are null; dates are milliseconds since the
 * Unix epoch.
 */
data class PdfInfo(
  val title: String? = null,
  val author: String? = null,
  val subject: String? = null,
  val keywords: String? = null,
  val creator: String? = null,
  val producer: String? = null,
  val createdAtMs: Long? = null,
  val modifiedAtMs: Long? = null,
)
