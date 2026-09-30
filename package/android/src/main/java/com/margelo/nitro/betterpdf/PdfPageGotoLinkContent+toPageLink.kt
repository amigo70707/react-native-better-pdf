package com.margelo.nitro.betterpdf

import android.graphics.pdf.content.PdfPageGotoLinkContent
import android.os.Build
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun PdfPageGotoLinkContent.toPageLink(): PageLink.Goto =
  PageLink.Goto(bounds.map { it.toPageRect() }, destination.pageNumber)
