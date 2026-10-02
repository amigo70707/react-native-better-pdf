import Foundation

/// A downloaded copy of a remote document in the `DocumentCacheStore`.
struct CachedCopy {
  let fileURL: URL
  let mode: PdfCacheMode
  let metadata: CacheMetadata
}
