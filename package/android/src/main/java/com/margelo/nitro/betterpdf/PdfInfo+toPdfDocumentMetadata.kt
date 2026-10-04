package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.outline.PdfInfo

internal fun PdfInfo.toPdfDocumentMetadata(): PdfDocumentMetadata =
  PdfDocumentMetadata(
    title = title,
    author = author,
    subject = subject,
    keywords = keywords,
    creator = creator,
    producer = producer,
    createdAtMs = createdAtMs?.toDouble(),
    modifiedAtMs = modifiedAtMs?.toDouble(),
  )
