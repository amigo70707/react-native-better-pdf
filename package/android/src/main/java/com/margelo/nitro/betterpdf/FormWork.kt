package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.outline.FormFieldEntry

/**
 * Work on the document's form, done on the render thread in the order it
 * was asked for and never dropped while the document is open.
 */
internal sealed interface FormWork : DocumentWork {
  /** The reader changed a widget. */
  data class ReaderEdit(val edit: FormEdit) : FormWork

  /** `getFormFields`: [done] on the main thread. */
  class ReadFields(val done: (Result<List<FormFieldEntry>>) -> Unit) : FormWork

  /** `setFormFieldValues`: [done] on the main thread, with the error that stopped it. */
  class SetValues(val values: List<NativePdfFormValue>, val done: (Throwable?) -> Unit) : FormWork

  /** [done] on the main thread once earlier form work is done. */
  class Barrier(val done: () -> Unit) : FormWork

  /** Fails [this] work that will not run (the document closed). Main thread. */
  fun fail(error: Throwable) {
    when (this) {
      is ReaderEdit -> Unit
      is ReadFields -> done(Result.failure(error))
      is SetValues -> done(error)
      is Barrier -> done()
    }
  }
}
