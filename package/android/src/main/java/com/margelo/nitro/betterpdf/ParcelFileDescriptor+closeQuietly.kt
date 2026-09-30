package com.margelo.nitro.betterpdf

import android.os.ParcelFileDescriptor
import java.io.IOException

/** Closes the descriptor, ignoring errors (it may already be closed by a renderer). */
internal fun ParcelFileDescriptor.closeQuietly() {
  try {
    close()
  } catch (_: IOException) {
  }
}
