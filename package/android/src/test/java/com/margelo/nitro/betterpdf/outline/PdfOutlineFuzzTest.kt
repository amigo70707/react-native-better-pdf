package com.margelo.nitro.betterpdf.outline

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Deterministic mutation fuzzing: the reader parses untrusted files, so any
 * corruption must end in a result or a [PdfFormatException] — never another
 * exception, a hang or a stack overflow.
 */
class PdfOutlineFuzzTest {
  private val seeds = listOf("outline.pdf", "outline-compressed.pdf", "outline-pypdf.pdf").map { name ->
    javaClass.getResourceAsStream("/outline/$name")!!.use { it.readBytes() }
  }

  @Test
  fun `corrupted files fail cleanly`() {
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
        PdfOutlineReader(PdfDocumentReader(ByteArraySource(mutated))).read()
        parsed++
      } catch (expected: PdfFormatException) {
        // Fine.
      }
      val millis = (System.nanoTime() - started) / 1_000_000
      assertTrue("iteration $iteration took $millis ms", millis < 2_000)
    }
    // Most single-byte damage is recoverable, as in real viewers.
    assertTrue("parsed $parsed of 500", parsed > 100)
  }
}
