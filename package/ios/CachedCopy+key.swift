extension CachedCopy {
  /// The cache key the copy is stored under (its file name).
  var key: String { fileURL.deletingPathExtension().lastPathComponent }
}
