package com.margelo.nitro.betterpdf

/**
 * A change to one form widget, applied by the renderer and kept to apply
 * again on a new renderer (they start from the file).
 */
sealed interface FormEdit {
  val page: Int
  val widgetIndex: Int

  /** A tap at a point of the page (PDF points): toggles a checkbox or radio button. */
  data class Click(override val page: Int, override val widgetIndex: Int, val x: Int, val y: Int) : FormEdit

  data class SetText(override val page: Int, override val widgetIndex: Int, val text: String) : FormEdit

  /** Selects a choice's options by index; empty selects none. */
  class SetIndices(override val page: Int, override val widgetIndex: Int, val indices: IntArray) : FormEdit
}
