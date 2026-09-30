package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.outline.OutlineEntry

internal fun OutlineEntry.toPdfOutlineEntry(): PdfOutlineEntry =
  PdfOutlineEntry(title = title, page = page?.toDouble(), depth = depth.toDouble())
