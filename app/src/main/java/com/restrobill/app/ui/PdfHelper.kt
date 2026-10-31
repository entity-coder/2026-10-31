package com.restrobill.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal PDF output shared by bills and KOTs: plain-text lines drawn on
 * A4 pages, paginated when the page fills. No third-party dependency —
 * android.graphics.pdf.PdfDocument is in the framework.
 */
object PdfHelper {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val LINE_H = 26

    /** Render [lines] (first line drawn bold, as a heading) to a file. */
    fun writePdf(context: Context, fileName: String, lines: List<String>): File {
        val doc = PdfDocument()
        val normal = Paint().apply { textSize = 15f }
        val bold = Paint().apply { textSize = 17f; isFakeBoldText = true }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN + 20
        var pageNo = 1
        for ((index, line) in lines.withIndex()) {
            if (y > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN + 20
            }
            canvas.drawText(line, MARGIN.toFloat(), y.toFloat(), if (index == 0) bold else normal)
            y += LINE_H
        }
        doc.finishPage(page)
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Send the PDF to the system print dialog. */
    fun printPdf(context: Context, file: File, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                callback?.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name).setContentType(
                        PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
                    ).build(), true
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileOutputStream(destination?.fileDescriptor).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }, null)
    }

    /** Share the PDF with any app (WhatsApp, Drive, Files, ...). */
    fun sharePdf(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
