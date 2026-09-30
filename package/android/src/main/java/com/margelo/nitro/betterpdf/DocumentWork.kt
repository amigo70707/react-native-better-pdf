package com.margelo.nitro.betterpdf

/**
 * Work for a document's render thread, the only thread that opens pages:
 * page renders ([RenderRequest]), text selections ([TextSelectionQuery])
 * and page text for screen readers ([PageTextQuery]).
 */
sealed interface DocumentWork
