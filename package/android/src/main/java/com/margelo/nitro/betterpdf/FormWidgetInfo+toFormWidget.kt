package com.margelo.nitro.betterpdf

import android.graphics.pdf.models.FormEditRecord
import android.graphics.pdf.models.FormWidgetInfo
import android.graphics.Point
import android.os.Build
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun FormWidgetInfo.toFormWidget(): FormWidget = FormWidget(
  index = widgetIndex,
  type = when (widgetType) {
    FormWidgetInfo.WIDGET_TYPE_TEXTFIELD -> FormWidget.Type.TEXT
    FormWidgetInfo.WIDGET_TYPE_CHECKBOX -> FormWidget.Type.CHECKBOX
    FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON -> FormWidget.Type.RADIO
    FormWidgetInfo.WIDGET_TYPE_COMBOBOX -> FormWidget.Type.COMBO_BOX
    FormWidgetInfo.WIDGET_TYPE_LISTBOX -> FormWidget.Type.LIST_BOX
    FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON -> FormWidget.Type.PUSH_BUTTON
    FormWidgetInfo.WIDGET_TYPE_SIGNATURE -> FormWidget.Type.SIGNATURE
    else -> FormWidget.Type.UNKNOWN
  },
  rect = widgetRect.let { PageRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
  isReadOnly = isReadOnly,
  isEditableText = isEditableText,
  isMultiline = isMultiLineText,
  isMultiSelect = isMultiSelect,
  maxLength = maxLength,
  fontSize = fontSize,
  textValue = textValue.orEmpty(),
  items = listItems.map { FormWidget.Item(it.label, it.isSelected) },
)

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun FormEdit.toRecord(): FormEditRecord = when (this) {
  is FormEdit.Click ->
    FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_CLICK, page, widgetIndex).setClickPoint(Point(x, y)).build()
  is FormEdit.SetText ->
    FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_SET_TEXT, page, widgetIndex).setText(text).build()
  is FormEdit.SetIndices ->
    FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_SET_INDICES, page, widgetIndex).setSelectedIndices(indices).build()
}
