package com.margelo.nitro.betterpdf

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.react.uimanager.ThemedReactContext
import com.margelo.nitro.betterpdf.outline.withLineFeeds
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fills the form in by touch, on the UI thread: a checkbox or radio button
 * toggles at once, a choice opens a list to pick from, and a text field is
 * edited in place — an [EditText] over the widget in [container], following
 * it as the page scrolls and zooms, scrolled above the keyboard, and
 * applied as one edit when the reader is done: the keyboard's action, a
 * tap elsewhere, its page leaving the screen, or [commit] (before code
 * reads or writes the form).
 */
internal class FormFiller(
  private val container: FrameLayout,
  private val documentView: PdfDocumentView,
) {
  private var editing: Pair<Int, FormWidget>? = null
  private var field: EditText? = null
  private val rect = RectF()
  private val location = IntArray(2)

  init {
    // Edge-to-edge windows do not shrink for the keyboard; its inset does.
    ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
      if (editing != null) container.post(::keepAboveKeyboard)
      insets
    }
  }

  fun onWidgetTapped(page: Int, widget: FormWidget) {
    commit()
    when (widget.type) {
      FormWidget.Type.CHECKBOX, FormWidget.Type.RADIO ->
        documentView.editForm(PlacedWidget(page, widget).click())
      FormWidget.Type.TEXT -> startEditing(page, widget)
      FormWidget.Type.COMBO_BOX, FormWidget.Type.LIST_BOX -> choose(page, widget)
      else -> Unit
    }
  }

  /** Applies text being typed, if any, and closes the editor. */
  fun commit() {
    val (page, widget) = editing ?: return
    val editText = field ?: return
    editing = null
    field = null
    val text = editText.text.toString()
    inputMethodManager?.hideSoftInputFromWindow(editText.windowToken, 0)
    container.removeView(editText)
    if (text != widget.textValue.withLineFeeds()) documentView.editForm(FormEdit.SetText(page, widget.index, text))
  }

  /** Closes the editor without applying its text (the document is going away). */
  fun cancel() {
    val editText = field ?: return
    editing = null
    field = null
    inputMethodManager?.hideSoftInputFromWindow(editText.windowToken, 0)
    container.removeView(editText)
  }

  /** Follows the widget; commits when its page has left the screen. Call whenever pages move. */
  fun place() {
    val (page, widget) = editing ?: return
    val editText = field ?: return
    val bounds = widget.rect
    if (!documentView.pageRectInView(page, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top, rect)) {
      commit()
      return
    }
    val width = max(1, rect.width().roundToInt())
    val height = max(1, rect.height().roundToInt())
    val pixelsPerPoint = rect.height() / max(1f, bounds.bottom - bounds.top)
    // 0 means automatic: fit a line in the field, as PDFium does.
    val points = if (widget.fontSize > 0f) widget.fontSize else min(12f, (bounds.bottom - bounds.top) * 0.7f)
    editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, points * pixelsPerPoint)
    // Laid out here: React Native does not lay out views it did not create.
    editText.layoutParams = FrameLayout.LayoutParams(width, height)
    editText.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    val left = rect.left.roundToInt()
    val top = rect.top.roundToInt()
    editText.layout(left, top, left + width, top + height)
  }

  private fun startEditing(page: Int, widget: FormWidget) {
    val context = container.context
    val editText = EditText(context).apply {
      setText(widget.textValue.withLineFeeds())
      setTextColor(Color.BLACK)
      val multiline = widget.isMultiline
      isSingleLine = !multiline
      inputType = InputType.TYPE_CLASS_TEXT or (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
      imeOptions = EditorInfo.IME_ACTION_DONE
      gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL or Gravity.START
      val padding = (2 * resources.displayMetrics.density).roundToInt()
      setPadding(padding, 0, padding, 0)
      background = GradientDrawable().apply {
        setColor(Color.WHITE)
        setStroke(max(1, (resources.displayMetrics.density).roundToInt()), FIELD_BORDER)
      }
      if (widget.maxLength > 0) filters = arrayOf(InputFilter.LengthFilter(widget.maxLength))
      setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          commit()
          true
        } else {
          false
        }
      }
      setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit() }
    }
    editing = page to widget
    field = editText
    container.addView(editText, FrameLayout.LayoutParams(0, 0))
    place()
    editText.requestFocus()
    editText.setSelection(editText.text.length)
    editText.post { inputMethodManager?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT) }
  }

  /** Scrolls the document so the field being edited is above the keyboard. */
  private fun keepAboveKeyboard() {
    val editText = field ?: return
    val insets = ViewCompat.getRootWindowInsets(container) ?: return
    val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    if (keyboard <= 0) return
    container.getLocationInWindow(location)
    val windowHeight = container.rootView.height
    val visibleBottom = min(container.height - documentView.bottomClearance.roundToInt(), windowHeight - keyboard - location[1])
    val margin = 16 * container.resources.displayMetrics.density
    val overlap = editText.bottom + margin - visibleBottom
    if (overlap > 0) documentView.panBy(0f, overlap)
  }

  private fun choose(page: Int, widget: FormWidget) {
    val activity = (container.context as? ThemedReactContext)?.currentActivity ?: return
    val labels = widget.items.map { it.label }.toTypedArray()
    val builder = AlertDialog.Builder(activity)
    if (widget.isMultiSelect) {
      val checked = widget.items.map { it.isSelected }.toBooleanArray()
      builder
        .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
        .setPositiveButton(android.R.string.ok) { _, _ ->
          val indices = checked.indices.filter { checked[it] }.toIntArray()
          documentView.editForm(FormEdit.SetIndices(page, widget.index, indices))
        }
        .setNegativeButton(android.R.string.cancel, null)
    } else {
      val selected = widget.items.indexOfFirst { it.isSelected }
      builder.setSingleChoiceItems(labels, selected) { dialog, which ->
        dialog.dismiss()
        if (which != selected) documentView.editForm(FormEdit.SetIndices(page, widget.index, intArrayOf(which)))
      }
    }
    builder.show()
  }

  private val inputMethodManager: InputMethodManager?
    get() = container.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

  private companion object {
    val FIELD_BORDER = Color.rgb(37, 99, 235)
  }
}
