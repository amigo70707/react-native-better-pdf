package com.margelo.nitro.betterpdf

/**
 * Joins a page's text runs as the platform returns them, with "\r\n" line
 * ends normalised to "\n" (as for selections) and surrounding blank space
 * trimmed; empty pages yield "".
 */
internal fun joinPageText(runs: List<String>): String =
  runs.joinToString("").replace(Regex("\r\n?"), "\n").trim()
