package com.restrobill.app.data

import java.text.NumberFormat
import java.util.Locale

/** Format integer paise as ₹ with Indian grouping (₹1,234.50). */
fun formatRupees(paise: Long): String {
    val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 2
    return nf.format(paise / 100.0)
}
