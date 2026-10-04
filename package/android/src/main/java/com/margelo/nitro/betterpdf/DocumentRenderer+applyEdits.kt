package com.margelo.nitro.betterpdf

/**
 * Applies a form's [edits] in order: renderers start from the file, so a
 * new one gets them again before it renders or writes. Edits it refuses
 * (a field that became read-only, say) are skipped. Opens each edited page
 * once per run of edits on it.
 */
internal fun DocumentRenderer.applyEdits(edits: List<FormEdit>) {
  var index = 0
  while (index < edits.size) {
    val page = edits[index].page
    openPage(page).use { open ->
      while (index < edits.size && edits[index].page == page) {
        try {
          open.applyFormEdit(edits[index])
        } catch (_: IllegalArgumentException) {
        }
        index++
      }
    }
  }
}
