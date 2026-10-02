package com.margelo.nitro.betterpdf

internal fun CachedCopy.toPdfCacheEntry(): PdfCacheEntry =
  PdfCacheEntry(
    filePath = file.path,
    byteSize = file.length().toDouble(),
    cache = mode,
    validatedAtMs = metadata.validatedAtMs.toDouble(),
  )
