package com.restrobill.app.ui

import android.content.Context
import com.restrobill.app.data.Bill
import com.restrobill.app.data.BillLine
import com.restrobill.app.data.BillTotals
import com.restrobill.app.data.OrderLine
import com.restrobill.app.data.ShopPrefs
import com.restrobill.app.data.formatRupees
import java.io.File

/** Build the printable bill text. All figures come from the stored Bill
 *  row, so a reprinted bill can never differ from the original. */
fun buildBillPdf(
    context: Context,
    prefs: ShopPrefs,
    bill: Bill,
    lines: List<BillLine>,
    totals: BillTotals,
    gstPercent: Int
): File {
    val out = mutableListOf(prefs.restaurantName, "Bill ${bill.billNo}  •  Table ${bill.tableLabel}")
    out += "----------------------------------------"
    for (line in lines) {
        out += "${line.qty} x ${line.nameSnapshot}  ${formatRupees(line.amountPaise)}"
    }
    out += "----------------------------------------"
    out += "Subtotal: ${formatRupees(totals.subtotalPaise)}"
    if (totals.discountPaise > 0) out += "Discount: -${formatRupees(totals.discountPaise)}"
    out += "GST ($gstPercent%): ${formatRupees(totals.cgstPaise + totals.sgstPaise)}" +
        " (CGST ${formatRupees(totals.cgstPaise)} + SGST ${formatRupees(totals.sgstPaise)})"
    out += "TOTAL: ${formatRupees(totals.totalPaise)}"
    out += "Paid by ${bill.paymentMode}  •  Thank you!"
    return PdfHelper.writePdf(context, "${bill.billNo}.pdf", out)
}

/** Build a kitchen order ticket. Only the lines passed in are printed, so
 *  a second KOT never re-sends dishes the kitchen already has — unless
 *  the waiter explicitly reprints, in which case all lines are passed. */
fun buildKotPdf(
    context: Context,
    prefs: ShopPrefs,
    tableLabel: String,
    lines: List<OrderLine>,
    reprint: Boolean
): File {
    val out = mutableListOf(
        if (reprint) "KOT — REPRINT — Table $tableLabel" else "KOT — Table $tableLabel"
    )
    out += prefs.restaurantName
    out += "----------------------------------------"
    for (line in lines) {
        out += "${line.qty} x ${line.nameSnapshot}"
        if (line.note.isNotBlank()) out += "    Note: ${line.note}"
    }
    out += "----------------------------------------"
    out += "Total items: ${lines.sumOf { it.qty }}"
    return PdfHelper.writePdf(
        context, "KOT-$tableLabel-${System.currentTimeMillis()}.pdf", out
    )
}
