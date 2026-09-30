package com.margelo.nitro.betterpdf

import android.graphics.pdf.content.PdfPageLinkContent
import android.os.Build
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun PdfPageLinkContent.toPageLink(): PageLink.Url =
  PageLink.Url(bounds.map { it.toPageRect() }, uri.toString())
