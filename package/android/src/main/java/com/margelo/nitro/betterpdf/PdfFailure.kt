package com.margelo.nitro.betterpdf

/** A load/render failure, convertible to the JS-facing [PdfError]. */
class PdfFailure(
  val code: PdfErrorCode,
  override val message: String,
  val page: Int? = null,
  val httpStatus: Int? = null,
  cause: Throwable? = null,
) : Exception(message, cause) {
  fun toPdfError(): PdfError =
    PdfError(
      code = code,
      message = message,
      page = page?.toDouble(),
      httpStatus = httpStatus?.toDouble(),
      nativeDescription = cause?.let { "${it.javaClass.simpleName}: ${it.message}" },
    )
}
