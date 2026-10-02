extension CachedCopy {
  func toPdfCacheEntry() -> PdfCacheEntry {
    return PdfCacheEntry(
      filePath: fileURL.path,
      byteSize: Double(fileURL.fileSize()),
      cache: mode,
      validatedAtMs: Double(metadata.validatedAtMs)
    )
  }
}
