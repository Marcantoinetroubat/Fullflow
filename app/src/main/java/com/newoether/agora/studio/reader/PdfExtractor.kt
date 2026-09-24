package com.newoether.agora.studio.reader

import android.content.Context
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.File
import java.io.InputStream

object PdfExtractor {

    /**
     * Extracts text from a PDF file page by page using iTextG.
     */
    fun extractTextFromPdf(context: Context, file: File): String {
        return try {
            val reader = PdfReader(file.absolutePath)
            val textBuilder = StringBuilder()
            val pages = reader.numberOfPages
            for (i in 1..pages) {
                val pageText = PdfTextExtractor.getTextFromPage(reader, i) ?: ""
                textBuilder.append(pageText).append("\n")
            }
            reader.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            "Error extracting PDF: ${e.localizedMessage}"
        }
    }

    /**
     * Extracts text from an input stream (useful for content:// URIs).
     */
    fun extractTextFromPdfStream(context: Context, inputStream: InputStream): String {
        return try {
            val reader = PdfReader(inputStream)
            val textBuilder = StringBuilder()
            val pages = reader.numberOfPages
            for (i in 1..pages) {
                val pageText = PdfTextExtractor.getTextFromPage(reader, i) ?: ""
                textBuilder.append(pageText).append("\n")
            }
            reader.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            "Error extracting PDF stream: ${e.localizedMessage}"
        }
    }
}
