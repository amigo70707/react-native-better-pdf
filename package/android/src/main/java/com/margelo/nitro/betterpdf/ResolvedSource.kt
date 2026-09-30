package com.margelo.nitro.betterpdf

import android.os.ParcelFileDescriptor
import java.io.File

/** An opened source: its descriptor, and the file behind it when there is one (not for `content://`). */
class ResolvedSource(val descriptor: ParcelFileDescriptor, val file: File?)
