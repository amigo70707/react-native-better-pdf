package com.margelo.nitro.betterpdf.outline

/**
 * Reads the document outline (ISO 32000-1 §12.3.3) and resolves every
 * entry to a page index: explicit destinations, GoTo actions, and named
 * destinations from the catalog's `/Dests` dictionary (PDF 1.1) or the
 * `/Names` → `/Dests` name tree.
 *
 * Cycles are broken and the walk is limited to [MAX_DEPTH] levels and
 * [MAX_ENTRIES] entries — the same limits as the iOS implementation.
 */
class PdfOutlineReader(private val document: PdfDocumentReader) {
  private val catalog = document.resolve(document.trailer["Root"]) as? PdfObject.Dictionary
    ?: throw PdfFormatException("No document catalog")
  private val pageIndices: Map<Int, Int> by lazy(::indexPages)
  private val pageCount: Int get() = pageIndices.size
  private val catalogDests: PdfObject.Dictionary? by lazy {
    document.resolve(catalog["Dests"]) as? PdfObject.Dictionary
  }
  private val nameTreeDests: Map<String, PdfObject> by lazy(::readDestsNameTree)

  fun read(): List<OutlineEntry> {
    val outlines = document.resolve(catalog["Outlines"]) as? PdfObject.Dictionary ?: return emptyList()
    val entries = ArrayList<OutlineEntry>()
    walk(outlines["First"], depth = 0, entries, visited = HashSet())
    return entries
  }

  private fun walk(first: PdfObject?, depth: Int, entries: MutableList<OutlineEntry>, visited: MutableSet<Int>) {
    var current = first
    while (current != null && entries.size < MAX_ENTRIES) {
      val reference = current as? PdfObject.Reference
      if (reference != null && !visited.add(reference.number)) return
      val item = document.resolve(current) as? PdfObject.Dictionary ?: return
      entries += OutlineEntry(titleOf(item), pageOf(item), depth)
      if (depth + 1 < MAX_DEPTH) walk(item["First"], depth + 1, entries, visited)
      current = item["Next"]
    }
  }

  private fun titleOf(item: PdfObject.Dictionary): String {
    val title = (document.resolve(item["Title"]) as? PdfObject.Str)?.toText() ?: return ""
    // Line breaks and tabs would break a single-line list row.
    return title.replace(CONTROL_CHARACTERS, " ").trim().take(MAX_TITLE_LENGTH)
  }

  private fun pageOf(item: PdfObject.Dictionary): Int? {
    item["Dest"]?.let { return pageOfDestination(it, hops = 0) }
    val action = document.resolve(item["A"]) as? PdfObject.Dictionary ?: return null
    if (action["S"] != PdfObject.Name("GoTo")) return null
    return pageOfDestination(action["D"], hops = 0)
  }

  private fun pageOfDestination(destination: PdfObject?, hops: Int): Int? {
    if (hops > MAX_DESTINATION_HOPS) return null
    return when (val value = document.resolve(destination)) {
      is PdfObject.Array -> when (val target = value.items.firstOrNull()) {
        is PdfObject.Reference -> pageIndices[target.number]
        // Page numbers are meant for remote documents, but some producers use them locally.
        is PdfObject.Number -> target.intValue?.takeIf { it in 0 until pageCount }
        else -> null
      }
      // PDF 1.1: a name looked up in the catalog's /Dests dictionary.
      is PdfObject.Name -> pageOfDestination(catalogDests?.get(value.value), hops + 1)
      // PDF 1.2+: a string looked up in the /Dests name tree.
      is PdfObject.Str -> pageOfDestination(nameTreeDests[keyOf(value)], hops + 1)
      // A destination dictionary wraps the array in /D.
      is PdfObject.Dictionary -> pageOfDestination(value["D"], hops + 1)
      else -> null
    }
  }

  /** Name-tree keys are byte strings; compare them byte for byte. */
  private fun keyOf(value: PdfObject.Str): String = value.bytes.toString(Charsets.ISO_8859_1)

  /** Page object number → zero-based index, walking the page tree in order. */
  private fun indexPages(): Map<Int, Int> {
    val indices = HashMap<Int, Int>()
    val visited = HashSet<Int>()
    fun visit(node: PdfObject?, depth: Int) {
      if (depth > MAX_TREE_DEPTH || indices.size >= MAX_PAGES) return
      val reference = node as? PdfObject.Reference
      if (reference != null && !visited.add(reference.number)) return
      val dictionary = document.resolve(node) as? PdfObject.Dictionary ?: return
      val kids = document.resolve(dictionary["Kids"]) as? PdfObject.Array
      if (kids != null && dictionary["Type"] != PdfObject.Name("Page")) {
        kids.items.forEach { visit(it, depth + 1) }
      } else if (reference != null) {
        indices[reference.number] = indices.size
      }
    }
    visit(catalog["Pages"], depth = 0)
    return indices
  }

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

  companion object {
    /** Shared with iOS (`PdfOutlineReader.swift`). */
    const val MAX_DEPTH = 64
    const val MAX_ENTRIES = 10_000
    private const val MAX_TITLE_LENGTH = 512
    private const val MAX_DESTINATION_HOPS = 4
    private const val MAX_TREE_DEPTH = 64
    private const val MAX_PAGES = 1_000_000
    private const val MAX_NAMED_DESTINATIONS = 1_000_000
    private val CONTROL_CHARACTERS = Regex("[\\u0000-\\u001F\\u007F]+")
  }
}
