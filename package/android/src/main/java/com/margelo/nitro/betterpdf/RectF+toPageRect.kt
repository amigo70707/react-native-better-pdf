package com.margelo.nitro.betterpdf

import android.graphics.RectF

internal fun RectF.toPageRect(): PageRect = PageRect(left, top, right, bottom)
