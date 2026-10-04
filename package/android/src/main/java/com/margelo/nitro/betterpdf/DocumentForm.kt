package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.outline.FormFieldEntry
import com.margelo.nitro.betterpdf.outline.FormFieldKind

/**
 * A document's form on its render thread. The fields — names, types, the
 * values the file has — come from the AcroForm tree ([readFields], on first
 * use; the renderer reports widgets without names); values then follow
 * the renderer's widgets as pages are read ([updatePage]). Edits applied
 * to the renderer are kept ([edits], read by other threads) to apply again
 * on new renderers, which start from the file.
 */
internal class DocumentForm(private val readFields: () -> List<FormFieldEntry>) {
  /** Every edit applied, in order. */
  @Volatile
  var edits: List<FormEdit> = emptyList()
    private set

  private var fields: List<FormFieldEntry>? = null
  /** The renderer's widgets of each page read so far. */
  private val pageWidgets = HashMap<Int, List<FormWidget>>()
  /** For each field, the renderer widget of each of its widgets (null where unread or unmatched). */
  private var placed: List<Array<PlacedWidget?>> = emptyList()

  /** The fields with their current values; reads the form on first call (throws when it cannot). */
  fun fields(): List<FormFieldEntry> {
    fields?.let { return it }
    val read = readFields()
    placed = read.map { arrayOfNulls(it.widgets.size) }
    fields = read
    pageWidgets.keys.forEach(::matchPage)
    return fields!!
  }

  val hasReadFields: Boolean get() = fields != null

  fun widgetsOf(page: Int): List<FormWidget>? = pageWidgets[page]

  /** The renderer widgets of field [index], by its widgets. */
  fun placedWidgets(index: Int): List<PlacedWidget?> = placed[index].toList()

  /** Records [page]'s widgets as the renderer has them now, and the values they show. */
  fun updatePage(page: Int, widgets: List<FormWidget>) {
    pageWidgets[page] = widgets
    if (fields != null) matchPage(page)
  }

  fun record(edit: FormEdit) {
    edits = edits + edit
  }

  /** The field shown by renderer widget [widgetIndex] of [page], with its value; null when unknown. */
  fun fieldAt(page: Int, widgetIndex: Int): FormFieldEntry? {
    val all = fields ?: return null
    val index = placed.indexOfFirst { widgets -> widgets.any { it?.page == page && it.widget.index == widgetIndex } }
    return all.getOrNull(index)
  }

  private fun matchPage(page: Int) {
    val all = fields ?: return
    val widgets = pageWidgets[page] ?: return
    // Field widgets on this page, in field order; the renderer omits signatures.
    val slots = ArrayList<Pair<Int, Int>>()
    all.forEachIndexed { fieldIndex, field ->
      if (field.kind == FormFieldKind.SIGNATURE) return@forEachIndexed
      field.widgets.forEachIndexed { widgetIndex, widget -> if (widget.page == page) slots += fieldIndex to widgetIndex }
    }
    val matches = FormWidgetMatcher.match(
      slots.map { (fieldIndex, widgetIndex) ->
        all[fieldIndex].widgets[widgetIndex].let { PageRect(it.left, it.top, it.left + it.width, it.top + it.height) }
      },
      widgets.map { it.rect },
    )
    val changed = HashSet<Int>()
    slots.forEachIndexed { slot, (fieldIndex, widgetIndex) ->
      placed[fieldIndex][widgetIndex] = widgets.getOrNull(matches[slot])?.let { PlacedWidget(page, it) }
      changed += fieldIndex
    }
    fields = all.mapIndexed { index, field ->
      if (index in changed) field.withRendererValues(placed[index].map { it?.widget }) else field
    }
  }
}
