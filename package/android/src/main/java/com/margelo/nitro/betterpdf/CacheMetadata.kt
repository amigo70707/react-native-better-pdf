package com.margelo.nitro.betterpdf

/**
 * What is known about a downloaded copy: when the server last confirmed it
 * current (download or `304 Not Modified`), and the validators to ask again.
 */
internal data class CacheMetadata(
  val validatedAtMs: Long,
  val etag: String?,
  val lastModified: String?,
) {
  /**
   * Whether the copy may be used without asking the server. A null
   * [maxAgeMs] never expires; `0` (and NaN) always asks. A clock that went
   * backwards also asks, so a wrong date can never pin an outdated copy.
   */
  fun isFresh(maxAgeMs: Double?, nowMs: Long): Boolean {
    if (maxAgeMs == null) return true
    val ageMs = nowMs - validatedAtMs
    return ageMs >= 0 && ageMs < maxAgeMs
  }
}
