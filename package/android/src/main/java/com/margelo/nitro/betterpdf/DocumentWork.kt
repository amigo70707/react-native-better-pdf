package com.margelo.nitro.betterpdf

/**
 * Work for a document's render thread, the only thread that opens pages:
 * page renders ([RenderRequest]) and text selections ([TextSelectionQuery]).
 */
sealed interface DocumentWork
