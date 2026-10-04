package com.margelo.nitro.betterpdf

/**
 * A form widget of an open page, as the renderer reports it (Android 15+,
 * or 12–14 with SDK extension 13): [index] is its place among the page's
 * widgets (signature widgets excluded, measured), [rect] in PDF points
 * from the page's top-left corner.
 */
data class FormWidget(
  val index: Int,
  val type: Type,
  val rect: PageRect,
  val isReadOnly: Boolean,
  val isEditableText: Boolean,
  val isMultiline: Boolean,
  val isMultiSelect: Boolean,
  /** -1 when unlimited. */
  val maxLength: Int,
  /** 0 for automatic. */
  val fontSize: Float,
  /** Text and choices: the value; checkbox and radio button: "true" or "false". */
  val textValue: String,
  /** Choices: the options, as displayed. */
  val items: List<Item>,
) {
  enum class Type { TEXT, CHECKBOX, RADIO, COMBO_BOX, LIST_BOX, PUSH_BUTTON, SIGNATURE, UNKNOWN }

  data class Item(val label: String, val isSelected: Boolean)

  val isOn: Boolean get() = textValue == "true"

  /** The reader can fill it in by touch: not read-only, and a kind the view handles. */
  val isFillable: Boolean
    get() = !isReadOnly && type in setOf(Type.TEXT, Type.CHECKBOX, Type.RADIO, Type.COMBO_BOX, Type.LIST_BOX)
}
