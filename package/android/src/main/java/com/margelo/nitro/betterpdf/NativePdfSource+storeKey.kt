package com.margelo.nitro.betterpdf

import java.security.MessageDigest

/**
 * File name of this source's downloaded copy: a hash of `cacheKey` when set,
 * else of `uri` and the headers (sorted, so their order does not matter).
 */
internal fun NativePdfSource.storeKey(): String {
  val material = cacheKey?.let { "key\n$it" }
    ?: ("uri\n$uri" + headers.orEmpty().toSortedMap().entries.joinToString("") { "\n${it.key}:${it.value}" })
  return MessageDigest.getInstance("SHA-256")
    .digest(material.toByteArray())
    .joinToString("") { "%02x".format(it) }
}
