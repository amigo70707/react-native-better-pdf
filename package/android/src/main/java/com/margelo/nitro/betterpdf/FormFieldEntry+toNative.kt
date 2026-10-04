package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.outline.FormFieldEntry
import com.margelo.nitro.betterpdf.outline.FormFieldKind

/** The field as the view reports it to JS: every member concrete. */
internal fun FormFieldEntry.toNative(): NativePdfFormField = NativePdfFormField(
  name = name,
  type = when (kind) {
    FormFieldKind.TEXT -> PdfFormFieldType.TEXT
    FormFieldKind.CHECKBOX -> PdfFormFieldType.CHECKBOX
    FormFieldKind.RADIO -> PdfFormFieldType.RADIO
    FormFieldKind.COMBO_BOX -> PdfFormFieldType.COMBO_BOX
    FormFieldKind.LIST_BOX -> PdfFormFieldType.LIST_BOX
    FormFieldKind.SIGNATURE -> PdfFormFieldType.SIGNATURE
    FormFieldKind.PUSH_BUTTON -> PdfFormFieldType.PUSH_BUTTON
  },
  isReadOnly = isReadOnly,
  isRequired = isRequired,
  widgets = widgets.map {
    PdfFormWidget(it.page.toDouble(), it.left.toDouble(), it.top.toDouble(), it.width.toDouble(), it.height.toDouble())
  }.toTypedArray(),
  text = if (kind == FormFieldKind.TEXT || kind == FormFieldKind.COMBO_BOX) text else "",
  isChecked = kind == FormFieldKind.CHECKBOX && isChecked,
  options = options.toTypedArray(),
  selectedOptions = selectedOptions.toTypedArray(),
  isMultiline = kind == FormFieldKind.TEXT && flags and FormFieldEntry.FLAG_MULTILINE != 0,
  maxLength = if (kind == FormFieldKind.TEXT) maxLength.toDouble() else 0.0,
  isMultiSelect = kind == FormFieldKind.LIST_BOX && flags and FormFieldEntry.FLAG_MULTI_SELECT != 0,
  isEditable = kind == FormFieldKind.COMBO_BOX && flags and FormFieldEntry.FLAG_EDIT != 0,
  isSigned = isSigned,
)
