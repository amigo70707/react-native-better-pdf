package com.margelo.nitro.betterpdf

/**
 * Reads a visible page's text for screen readers. Submitted with the render
 * work (so it is dropped with it when the page scrolls away), only while an
 * accessibility service is on.
 */
data class PageTextQuery(val page: Int) : DocumentWork
