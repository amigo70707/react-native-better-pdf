package com.margelo.nitro.betterpdf

import java.io.File

/** A downloaded copy of a remote document in the [DocumentCacheStore]. */
internal data class CachedCopy(
  val file: File,
  val mode: PdfCacheMode,
  val metadata: CacheMetadata,
)
