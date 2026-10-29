package com.example.medicat.data.transfer

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A printable A4 list of the cabinet, e.g. to take to a doctor or pharmacist. */
object CabinetPdf {
    private const val PAGE_WIDTH = 595 // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 20f

    /** Column title, share of the table width, and the text shown for a medication. */
    private class Col(val title: String, val share: Float, val text: (MedicationEntity) -> String)

    private val columns = listOf(
        Col("Medication", 0.34f) { it.name },
        Col("Strength", 0.16f) { it.strength },
        Col("Form", 0.11f) { it.form },
        Col("Left", 0.09f) { if (it.quantity > 0) "${it.remaining}/${it.quantity}" else "" },
        Col("Expires", 0.16f) { ExpiryDates.format(it.expiryDate) },
        Col("Kept in", 0.14f) { it.location }
    )

    fun write(data: CabinetData, out: OutputStream, today: LocalDate = LocalDate.now()) {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val small = Paint().apply { textSize = 10f; color = Color.DKGRAY; isAntiAlias = true }
        val head = Paint().apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val body = Paint().apply { textSize = 10f; color = Color.BLACK; isAntiAlias = true }
        val expired = Paint(body).apply { color = Color.rgb(0xC6, 0x28, 0x28) }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val tableWidth = PAGE_WIDTH - 2 * MARGIN
        val meds = data.medications.sortedBy { it.expiryDate }
        var pageNumber = 0
        var row = 0
        do {
            pageNumber++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            val canvas = page.canvas
            var y = MARGIN + 20f
            if (pageNumber == 1) {
                canvas.drawText("MediCab cabinet", MARGIN, y, title)
                y += 18f
                canvas.drawText(
                    "${meds.size} medications · printed ${today.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · red = expired",
                    MARGIN, y, small
                )
                y += 24f
            }
            var x = MARGIN
            columns.forEach { c -> canvas.drawText(c.title, x, y, head); x += c.share * tableWidth }
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
            y += ROW_HEIGHT - 6f
            while (row < meds.size && y < PAGE_HEIGHT - MARGIN - 20f) {
                val paint = if (ExpiryDates.daysUntil(meds[row].expiryDate, today) < 0) expired else body
                x = MARGIN
                columns.forEach { c ->
                    canvas.drawText(fit(c.text(meds[row]), paint, c.share * tableWidth - 6f), x, y, paint)
                    x += c.share * tableWidth
                }
                canvas.drawLine(MARGIN, y + 6f, PAGE_WIDTH - MARGIN, y + 6f, rule)
                y += ROW_HEIGHT
                row++
            }
            canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN - 40f, PAGE_HEIGHT - MARGIN / 2, small)
            doc.finishPage(page)
        } while (row < meds.size)
        doc.writeTo(out)
        doc.close()
    }

    /** Shortens [text] with an ellipsis so it fits in [width]. */
    private fun fit(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
        return text.take(end).trimEnd() + "…"
    }
}
