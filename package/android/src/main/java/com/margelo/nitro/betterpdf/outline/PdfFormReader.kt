package com.margelo.nitro.betterpdf.outline

/** Line breaks as `\n`: files and renderers store `\r\n` or `\r` too. */
fun String.withLineFeeds(): String = replace("\r\n", "\n").replace('\r', '\n')

/** The kind of a form field: its `/FT` and, for buttons and choices, its flags. */
enum class FormFieldKind { TEXT, CHECKBOX, RADIO, COMBO_BOX, LIST_BOX, SIGNATURE, PUSH_BUTTON }

/**
 * Where a widget of a form field is shown: zero-based page and a rectangle
 * in PDF points from the top-left corner of the page as displayed (crop
 * box, rotation applied), as `onPageTap` reports points.
 */
data class FormWidgetEntry(
  val page: Int,
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
  /** A checkbox or radio button's "on" state (its option), else null. */
  val onState: String?,
)

/** A form field as the file stores it (ISO 32000-1 §12.7). */
data class FormFieldEntry(
  /** Fully qualified: parent names first, joined by periods. */
  val name: String,
  val kind: FormFieldKind,
  /** `/Ff`, inherited. */
  val flags: Int,
  /** Text field: its value; combo box: the chosen option or typed text. */
  val text: String,
  val isChecked: Boolean,
  /** Radio: each widget's option; choices: the options' display text. */
  val options: List<String>,
  val selectedOptions: List<String>,
  /** 0 when unlimited. */
  val maxLength: Int,
  val isSigned: Boolean,
  val widgets: List<FormWidgetEntry>,
) {
  val isReadOnly: Boolean get() = flags and FLAG_READ_ONLY != 0
  val isRequired: Boolean get() = flags and FLAG_REQUIRED != 0

  companion object {
    const val FLAG_READ_ONLY = 1
    const val FLAG_REQUIRED = 1 shl 1
    const val FLAG_MULTILINE = 1 shl 12
    const val FLAG_NO_TOGGLE_TO_OFF = 1 shl 14
    const val FLAG_RADIO = 1 shl 15
    const val FLAG_PUSH_BUTTON = 1 shl 16
    const val FLAG_COMBO = 1 shl 17
    const val FLAG_EDIT = 1 shl 18
    const val FLAG_MULTI_SELECT = 1 shl 21
  }
}

/**
 * Reads the document's interactive form: every terminal field of the
 * AcroForm's `/Fields` tree, in order, with its inherited type, flags and
 * value and its widgets' places. Pages are found through their `/Annots`
 * (a widget's `/P` is optional). Cycles are broken; the walk is limited to
 * [MAX_DEPTH] levels and [MAX_FIELDS] fields.
 */
class PdfFormReader(private val document: PdfDocumentReader) {
  private val catalog = document.resolve(document.trailer["Root"]) as? PdfObject.Dictionary
    ?: throw PdfFormatException("No document catalog")

  /** `[]` without a form. */
  fun read(): List<FormFieldEntry> {
    val form = document.resolve(catalog["AcroForm"]) as? PdfObject.Dictionary ?: return emptyList()
    val fields = document.resolve(form["Fields"]) as? PdfObject.Array ?: return emptyList()
    val pages = readPages()
    val pageOfAnnotation = HashMap<Int, Int>()
    pages.forEachIndexed { index, page -> page.annotations.forEach { pageOfAnnotation.putIfAbsent(it, index) } }
    val pageIndexByObject = pages.withIndex().associate { (index, page) -> page.number to index }

    val result = ArrayList<FormFieldEntry>()
    val visited = HashSet<Int>()
    fun visit(node: PdfObject, parentName: String?, inherited: Inherited, depth: Int) {
      if (depth > MAX_DEPTH || result.size >= MAX_FIELDS) return
      val reference = node as? PdfObject.Reference
      if (reference != null && !visited.add(reference.number)) return
      val dictionary = document.resolve(node) as? PdfObject.Dictionary ?: return
      val partial = (document.resolve(dictionary["T"]) as? PdfObject.Str)?.toText()
      val name = when {
        partial == null -> parentName
        parentName == null -> partial
        else -> "$parentName.$partial"
      } ?: return
      val own = inherited.with(dictionary)
      val kids = (document.resolve(dictionary["Kids"]) as? PdfObject.Array)?.items.orEmpty()
      val (childFields, widgetKids) = kids.partition { kid ->
        (document.resolve(kid) as? PdfObject.Dictionary)?.get("T") != null
      }
      childFields.forEach { visit(it, name, own, depth + 1) }
      if (childFields.isNotEmpty() && widgetKids.isEmpty()) return
      // A field without kids is its own widget.
      val widgetNodes = if (kids.isEmpty()) listOf(node) else widgetKids
      val widgets = widgetNodes.mapNotNull { widgetNode ->
        val widget = document.resolve(widgetNode) as? PdfObject.Dictionary ?: return@mapNotNull null
        val number = (widgetNode as? PdfObject.Reference)?.number
        val pageIndex = number?.let(pageOfAnnotation::get)
          ?: (widget["P"] as? PdfObject.Reference)?.number?.let(pageIndexByObject::get)
          ?: return@mapNotNull null
        widgetEntry(widget, pageIndex, pages[pageIndex])
      }
      field(name, own, widgets, widgetNodes)?.let(result::add)
    }
    fields.items.forEach { visit(it, null, Inherited(), 0) }
    return result
  }

  /** Attributes a field takes from its ancestors (§12.7.3.1). */
  private data class Inherited(
    val type: String? = null,
    val flags: Int = 0,
    val value: PdfObject? = null,
    val options: PdfObject? = null,
    val maxLength: Int = 0,
  ) {
    fun with(dictionary: PdfObject.Dictionary) = copy(
      type = (dictionary["FT"] as? PdfObject.Name)?.value ?: type,
      flags = (dictionary["Ff"] as? PdfObject.Number)?.intValue ?: flags,
      value = dictionary["V"] ?: value,
      options = dictionary["Opt"] ?: options,
      maxLength = (dictionary["MaxLen"] as? PdfObject.Number)?.intValue ?: maxLength,
    )
  }

  private fun field(
    name: String,
    attributes: Inherited,
    widgets: List<FormWidgetEntry>,
    widgetNodes: List<PdfObject>,
  ): FormFieldEntry? {
    val flags = attributes.flags
    val kind = when (attributes.type) {
      "Tx" -> FormFieldKind.TEXT
      "Btn" -> when {
        flags and FormFieldEntry.FLAG_PUSH_BUTTON != 0 -> FormFieldKind.PUSH_BUTTON
        flags and FormFieldEntry.FLAG_RADIO != 0 -> FormFieldKind.RADIO
        else -> FormFieldKind.CHECKBOX
      }
      "Ch" -> if (flags and FormFieldEntry.FLAG_COMBO != 0) FormFieldKind.COMBO_BOX else FormFieldKind.LIST_BOX
      "Sig" -> FormFieldKind.SIGNATURE
      else -> return null
    }
    val value = document.resolve(attributes.value)
    var text = ""
    var isChecked = false
    var options = emptyList<String>()
    var selected = emptyList<String>()
    when (kind) {
      FormFieldKind.TEXT -> text = textOf(value)
      FormFieldKind.CHECKBOX -> {
        val state = (value as? PdfObject.Name)?.value ?: appearanceState(widgetNodes.firstOrNull())
        isChecked = state != null && state != OFF
      }
      FormFieldKind.RADIO -> {
        options = widgets.map { it.onState.orEmpty() }
        val state = (value as? PdfObject.Name)?.value
          ?: widgetNodes.firstNotNullOfOrNull { node -> appearanceState(node)?.takeIf { it != OFF } }
        selected = listOfNotNull(state?.takeIf { it != OFF && it in options })
      }
      FormFieldKind.COMBO_BOX, FormFieldKind.LIST_BOX -> {
        val choices = choicesOf(attributes.options)
        options = choices.map { it.second }
        // /V holds export values; report what the reader sees.
        val values = when (value) {
          is PdfObject.Str -> listOf(value.toText())
          is PdfObject.Array -> value.items.mapNotNull { (document.resolve(it) as? PdfObject.Str)?.toText() }
          else -> emptyList()
        }
        selected = values.map { exported -> choices.firstOrNull { it.first == exported }?.second ?: exported }
        if (kind == FormFieldKind.COMBO_BOX) text = selected.firstOrNull().orEmpty()
      }
      FormFieldKind.SIGNATURE, FormFieldKind.PUSH_BUTTON -> Unit
    }
    return FormFieldEntry(
      name = name,
      kind = kind,
      flags = flags,
      text = text,
      isChecked = isChecked,
      options = options,
      selectedOptions = selected,
      maxLength = attributes.maxLength.coerceAtLeast(0),
      isSigned = kind == FormFieldKind.SIGNATURE && value != null && value != PdfObject.Null,
      widgets = widgets,
    )
  }

  private fun textOf(value: PdfObject?): String = when (value) {
    is PdfObject.Str -> value.toText().withLineFeeds()
    else -> ""
  }

  /** `/Opt`: (export value, display text) pairs; plain entries are both. */
  private fun choicesOf(options: PdfObject?): List<Pair<String, String>> {
    val items = (document.resolve(options) as? PdfObject.Array)?.items ?: return emptyList()
    return items.mapNotNull { item ->
      when (val entry = document.resolve(item)) {
        is PdfObject.Str -> entry.toText().let { it to it }
        is PdfObject.Array -> {
          val export = (document.resolve(entry.items.getOrNull(0)) as? PdfObject.Str)?.toText()
          val display = (document.resolve(entry.items.getOrNull(1)) as? PdfObject.Str)?.toText()
          if (export != null) export to (display ?: export) else null
        }
        else -> null
      }
    }
  }

  private fun appearanceState(widgetNode: PdfObject?): String? =
    ((document.resolve(widgetNode) as? PdfObject.Dictionary)?.get("AS") as? PdfObject.Name)?.value

  private fun widgetEntry(widget: PdfObject.Dictionary, pageIndex: Int, page: PageGeometry): FormWidgetEntry? {
    val rect = rectangleOf(widget["Rect"]) ?: return null
    val normal = ((document.resolve(widget["AP"]) as? PdfObject.Dictionary)?.get("N"))
      .let { document.resolve(it) as? PdfObject.Dictionary }
    val onState = normal?.entries?.keys?.firstOrNull { it != OFF }
    val (left, top) = page.topLeft(rect[0], rect[3])
    val (right, bottom) = page.topLeft(rect[2], rect[1])
    return FormWidgetEntry(
      page = pageIndex,
      left = minOf(left, right),
      top = minOf(top, bottom),
      width = kotlin.math.abs(right - left),
      height = kotlin.math.abs(bottom - top),
      onState = onState,
    )
  }

  /** A page: its object number, displayed box and turns, and its annotations' object numbers. */
  private class PageGeometry(val number: Int, val box: FloatArray, val quarterTurns: Int, val annotations: List<Int>) {
    /** A point in PDF page space, from the displayed page's top-left corner. */
    fun topLeft(x: Float, y: Float): Pair<Float, Float> {
      val u = x - box[0]
      val v = box[3] - y
      val width = box[2] - box[0]
      val height = box[3] - box[1]
      return when (quarterTurns) {
        1 -> (height - v) to u
        2 -> (width - u) to (height - v)
        3 -> v to (width - u)
        else -> u to v
      }
    }
  }

  private fun readPages(): List<PageGeometry> {
    val pages = ArrayList<PageGeometry>()
    val visited = HashSet<Int>()
    fun visit(node: PdfObject?, mediaBox: FloatArray?, cropBox: FloatArray?, rotate: Int, depth: Int) {
      if (depth > MAX_DEPTH) return
      val reference = node as? PdfObject.Reference ?: return
      if (!visited.add(reference.number)) return
      val dictionary = document.resolve(node) as? PdfObject.Dictionary ?: return
      val ownMediaBox = rectangleOf(dictionary["MediaBox"]) ?: mediaBox
      val ownCropBox = rectangleOf(dictionary["CropBox"]) ?: cropBox
      val ownRotate = (document.resolve(dictionary["Rotate"]) as? PdfObject.Number)?.value?.toInt() ?: rotate
      val kids = document.resolve(dictionary["Kids"]) as? PdfObject.Array
      if (kids != null && dictionary["Type"] != PdfObject.Name("Page")) {
        kids.items.forEach { visit(it, ownMediaBox, ownCropBox, ownRotate, depth + 1) }
        return
      }
      val annotations = (document.resolve(dictionary["Annots"]) as? PdfObject.Array)?.items
        ?.mapNotNull { (it as? PdfObject.Reference)?.number }.orEmpty()
      pages += PageGeometry(reference.number, displayedBox(ownMediaBox, ownCropBox), Math.floorMod(ownRotate / 90, 4), annotations)
    }
    visit(catalog["Pages"], mediaBox = null, cropBox = null, rotate = 0, depth = 0)
    return pages
  }

  /** The crop box clipped to the media box, as `PdfPageSizeReader` and PDFium. */
  private fun displayedBox(mediaBox: FloatArray?, cropBox: FloatArray?): FloatArray {
    val media = mediaBox?.takeIf { it[2] > it[0] && it[3] > it[1] } ?: LETTER
    return cropBox?.let { crop ->
      floatArrayOf(maxOf(crop[0], media[0]), maxOf(crop[1], media[1]), minOf(crop[2], media[2]), minOf(crop[3], media[3]))
        .takeIf { it[2] > it[0] && it[3] > it[1] }
    } ?: media
  }

  /** `[llx lly urx ury]` normalized; null unless four numbers. */
  private fun rectangleOf(value: PdfObject?): FloatArray? {
    val items = (document.resolve(value) as? PdfObject.Array)?.items ?: return null
    if (items.size != 4) return null
    val numbers = items.map { (document.resolve(it) as? PdfObject.Number)?.value?.toFloat() ?: return null }
    return floatArrayOf(
      minOf(numbers[0], numbers[2]),
      minOf(numbers[1], numbers[3]),
      maxOf(numbers[0], numbers[2]),
      maxOf(numbers[1], numbers[3]),
    )
  }

  private companion object {
    const val OFF = "Off"
    const val MAX_DEPTH = 32
    const val MAX_FIELDS = 10_000
    val LETTER = floatArrayOf(0f, 0f, 612f, 792f)
  }
}
