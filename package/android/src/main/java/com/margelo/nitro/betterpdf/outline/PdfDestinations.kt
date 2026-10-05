package com.margelo.nitro.betterpdf.outline

/**
 * Resolves destinations (ISO 32000-1 §12.3.2) to zero-based pages: explicit
 * arrays, PDF 1.1 names in the catalog's `/Dests`, PDF 1.2+ strings in the
 * `/Dests` name tree, and destination dictionaries; and GoTo actions.
 */
class PdfDestinations(
  private val document: PdfDocumentReader,
  private val catalog: PdfObject.Dictionary,
  private val pageIndexByObject: () -> Map<Int, Int>,
) {
  private val pageIndices: Map<Int, Int> by lazy(pageIndexByObject)
  private val catalogDests: PdfObject.Dictionary? by lazy {
    document.resolve(catalog["Dests"]) as? PdfObject.Dictionary
  }
  private val nameTreeDests: Map<String, PdfObject> by lazy(::readDestsNameTree)

  /** The page of a GoTo action's destination; null for other actions. */
  fun pageOfAction(action: PdfObject.Dictionary): Int? {
    if (action["S"] != PdfObject.Name("GoTo")) return null
    return pageOf(action["D"])
  }

  fun pageOf(destination: PdfObject?): Int? = pageOf(destination, hops = 0)

  private fun pageOf(destination: PdfObject?, hops: Int): Int? {
    if (hops > MAX_HOPS) return null
    return when (val value = document.resolve(destination)) {
      is PdfObject.Array -> when (val target = value.items.firstOrNull()) {
        is PdfObject.Reference -> pageIndices[target.number]
        // Page numbers are meant for remote documents, but some producers use them locally.
        is PdfObject.Number -> target.intValue?.takeIf { it in 0 until pageIndices.size }
        else -> null
      }
      // PDF 1.1: a name looked up in the catalog's /Dests dictionary.
      is PdfObject.Name -> pageOf(catalogDests?.get(value.value), hops + 1)
      // PDF 1.2+: a string looked up in the /Dests name tree.
      is PdfObject.Str -> pageOf(nameTreeDests[keyOf(value)], hops + 1)
      // A destination dictionary wraps the array in /D.
      is PdfObject.Dictionary -> pageOf(value["D"], hops + 1)
      else -> null
    }
  }

  /** Name-tree keys are byte strings; compare them byte for byte. */
  private fun keyOf(value: PdfObject.Str): String = value.bytes.toString(Charsets.ISO_8859_1)

  private fun readDestsNameTree(): Map<String, PdfObject> {
    val names = document.resolve(catalog["Names"]) as? PdfObject.Dictionary ?: return emptyMap()
    val result = HashMap<String, PdfObject>()
    val visited = HashSet<Int>()
    fun visit(node: PdfObject?, depth: Int) {
      if (depth > MAX_TREE_DEPTH || result.size >= MAX_NAMED_DESTINATIONS) return
      val reference = node as? PdfObject.Reference
      if (reference != null && !visited.add(reference.number)) return
      val dictionary = document.resolve(node) as? PdfObject.Dictionary ?: return
      (document.resolve(dictionary["Names"]) as? PdfObject.Array)?.items?.chunked(2)?.forEach { pair ->
        val key = document.resolve(pair.getOrNull(0)) as? PdfObject.Str ?: return@forEach
        val value = pair.getOrNull(1) ?: return@forEach
        result.putIfAbsent(keyOf(key), value)
      }
      (document.resolve(dictionary["Kids"]) as? PdfObject.Array)?.items?.forEach { visit(it, depth + 1) }
    }
    visit(names["Dests"], depth = 0)
    return result
  }

  private companion object {
    const val MAX_HOPS = 4
    const val MAX_TREE_DEPTH = 64
    const val MAX_NAMED_DESTINATIONS = 1_000_000
  }
}
