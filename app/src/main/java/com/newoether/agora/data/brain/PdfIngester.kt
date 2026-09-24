package com.newoether.agora.data.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts text from a PDF file page-by-page. Uses PDFBox-Android when
 * available on the classpath; falls back to null (graceful degradation)
 * when the library is not yet integrated.
 *
 * To enable PDF extraction: add `implementation("com.github.TomRoush:PdfBox-Android:<version>")`
 * to app/build.gradle.kts and call `PDFBoxResourceLoader.init(context)` in
 * AgoraApplication.onCreate. The infrastructure (BrainRepository, entities,
 * DAOs) is already in place.
 */
object PdfIngester {

    data class PdfPageText(
        val pageNumber: Int,
        val text: String,
    )

    data class PdfExtractionResult(
        val title: String,
        val pages: List<PdfPageText>,
        val pageCount: Int,
    )

    suspend fun extract(file: File): PdfExtractionResult? = withContext(Dispatchers.IO) {
        // Check if PDFBox-Android is on the classpath via reflection.
        val pdfboxAvailable = try {
            Class.forName("com.tom_roush.pdfbox.pdmodel.PDDocument")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        if (!pdfboxAvailable) return@withContext null

        // When PDFBox is available, this code path activates:
        try {
            extractWithPdfBox(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractWithPdfBox(file: File): PdfExtractionResult? {
        // Uses reflection to avoid a hard compile-time dependency.
        val loaderClass = Class.forName("com.tom_roush.pdfbox.pdmodel.PDDocument")
        val stripperClass = Class.forName("com.tom_roush.pdfbox.text.PDFTextStripper")

        val loadMethod = loaderClass.getMethod("load", java.io.File::class.java)
        val document = loadMethod.invoke(null, file) ?: return null

        try {
            val pageCountMethod = loaderClass.getMethod("getNumberOfPages")
            val pageCount = pageCountMethod.invoke(document) as Int
            if (pageCount == 0) return null

            val stripper = stripperClass.getDeclaredConstructor().newInstance()
            val setStartPage = stripperClass.getMethod("setStartPage", Int::class.javaPrimitiveType)
            val setEndPage = stripperClass.getMethod("setEndPage", Int::class.javaPrimitiveType)
            val getText = stripperClass.getMethod("getText", loaderClass)

            val pages = mutableListOf<PdfPageText>()
            for (i in 1..pageCount) {
                setStartPage.invoke(stripper, i)
                setEndPage.invoke(stripper, i)
                val pageText = (getText.invoke(stripper, document) as String).trim()
                if (pageText.isNotBlank()) {
                    pages.add(PdfPageText(i, pageText.take(10_000)))
                }
            }

            return PdfExtractionResult(file.nameWithoutExtension, pages, pageCount)
        } finally {
            val closeMethod = loaderClass.getMethod("close")
            closeMethod.invoke(document)
        }
    }
}
