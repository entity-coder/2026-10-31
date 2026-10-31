package com.restrobill.app.data

import kotlin.math.roundToLong

/** Pure billing maths — no Android types, so the rules are easy to reason
 *  about and to test. All money is integer paise (Long). Dates are
 *  yyyy-MM-dd strings, which compare correctly as plain strings. */

fun lineTotalPaise(pricePaise: Long, qty: Int): Long = pricePaise * qty

/** Bill numbers run RB-0001, RB-0002, ... in insertion order. */
fun billNo(countSoFar: Int): String = "RB-%04d".format(countSoFar + 1)

enum class DiscountType { PERCENT, FLAT }

/**
 * Discount applied to the subtotal. A percent value is clamped to 0–100
 * and a flat value is capped at the subtotal, so a discount can never
 * make a bill negative.
 */
fun discountPaise(subtotalPaise: Long, type: DiscountType, value: Long): Long {
    return when (type) {
        DiscountType.PERCENT -> {
            val pct = value.coerceIn(0, 100)
            (subtotalPaise * pct / 100.0).roundToLong()
        }
        DiscountType.FLAT -> value.coerceIn(0, subtotalPaise)
    }
}

/**
 * GST charged on the discounted subtotal (the PC RestroBill rule: discount
 * first, then GST). Split into equal CGST/SGST halves; an odd paise goes
 * to CGST so cgst + sgst always equals the total GST.
 */
data class GstBreakup(val cgstPaise: Long, val sgstPaise: Long) {
    val totalPaise: Long get() = cgstPaise + sgstPaise
}

fun gstBreakup(discountedPaise: Long, gstPercent: Int): GstBreakup {
    if (gstPercent <= 0) return GstBreakup(0, 0)
    val gst = (discountedPaise * gstPercent / 100.0).roundToLong()
    val cgst = (gst + 1) / 2
    return GstBreakup(cgst, gst - cgst)
}

data class BillTotals(
    val subtotalPaise: Long,
    val discountPaise: Long,
    val discountedPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val totalPaise: Long
)

/** Full bill computation from order lines, in the order discount-then-GST. */
fun computeBill(
    lines: List<OrderLine>,
    discountType: DiscountType,
    discountValue: Long,
    gstPercent: Int
): BillTotals {
    val subtotal = lines.sumOf { lineTotalPaise(it.pricePaiseSnapshot, it.qty) }
    val discount = discountPaise(subtotal, discountType, discountValue)
    val discounted = subtotal - discount
    val gst = gstBreakup(discounted, gstPercent)
    return BillTotals(subtotal, discount, discounted, gst.cgstPaise, gst.sgstPaise, discounted + gst.totalPaise)
}

/** Parse a rupees string ("12.50", "12") into paise, or null when invalid. */
fun parseRupeesToPaise(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val bd = java.math.BigDecimal(trimmed)
        if (bd < java.math.BigDecimal.ZERO) null
        else bd.multiply(java.math.BigDecimal(100))
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    } catch (e: Exception) {
        null
    }
}

/** Today's date as yyyy-MM-dd in the device timezone. */
fun todayIso(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())
