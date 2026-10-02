package com.margelo.nitro.betterpdf

import java.io.File

/**
 * Whether the file starts like a PDF: `%PDF-` within its first 1024 bytes,
 * where readers accept it. Rejects error pages and login forms that servers
 * and captive portals return with a `200` status. Blocking.
 */
internal fun File.hasPdfHeader(): Boolean {
  val head = ByteArray(1024)
  val read = inputStream().use { input ->
    var total = 0
    while (total < head.size) {
      val count = input.read(head, total, head.size - total)
      if (count < 0) break
      total += count
    }
    total
  }
  return String(head, 0, read, Charsets.ISO_8859_1).contains("%PDF-")
}
