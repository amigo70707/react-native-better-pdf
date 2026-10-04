package com.margelo.nitro.betterpdf.outline

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Deterministic mutation fuzzing: the readers parse untrusted files, so any
 * corruption must end in a result or a [PdfFormatException] — never another
 * exception, a hang or a stack overflow.
 */
class PdfOutlineFuzzTest {
  private fun load(vararg names: String) = names.map { name ->
    javaClass.getResourceAsStream("/outline/$name")!!.use { it.readBytes() }
  }

  /**
   * Reads 500 corrupted copies of [seeds] with [read]; each must end in a
   * result or a [PdfFormatException] within 2 s. Returns how many parsed.
   */
  private fun fuzz(seeds: List<ByteArray>, read: (PdfDocumentReader) -> Unit): Int {
    val random = Random(20260930)
    var parsed = 0
    repeat(500) { iteration ->
      val bytes = seeds[iteration % seeds.size].copyOf()
      val mutated = when (iteration % 3) {
        0 -> bytes.also { repeat(1 + random.nextInt(40)) { _ -> it[random.nextInt(it.size)] = random.nextInt(256).toByte() } }
        1 -> bytes.copyOf(random.nextInt(bytes.size))
        // Structural damage: overwrite a run with PDF delimiters.
        else -> bytes.also {
          val start = random.nextInt(it.size)
          val tokens = "<<[(/ 0 R >>]".toByteArray()
          for (i in start until minOf(it.size, start + 64)) it[i] = tokens[random.nextInt(tokens.size)]
        }
      }
      val started = System.nanoTime()
      try {
        read(PdfDocumentReader(ByteArraySource(mutated)))
        parsed++
      } catch (expected: PdfFormatException) {
        // Fine.
      }
      val millis = (System.nanoTime() - started) / 1_000_000
      assertTrue("iteration $iteration took $millis ms", millis < 2_000)
    }
    return parsed
  }

  @Test
  fun `corrupted files fail cleanly`() {
    val parsed = fuzz(load("outline.pdf", "outline-compressed.pdf", "outline-pypdf.pdf")) { PdfOutlineReader(it).read() }
    // Most single-byte damage is recoverable, as in real viewers.
    assertTrue("parsed $parsed of 500", parsed > 100)
  }

  @Test
  fun `corrupted information dictionaries fail cleanly`() {
    val parsed = fuzz(load("metadata.pdf", "metadata-compressed.pdf", "outline-pdfkit.pdf")) { PdfInfoReader(it).read() }
    assertTrue("parsed $parsed of 500", parsed > 100)
  }
}
