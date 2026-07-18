package com.duitrip.app.ui.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** Formatting helpers ported from frontend/src/utils/{currency,date,flag}.ts. */
object Format {

    private val zeroDecimalCurrencies = setOf("IDR", "JPY", "KRW")

    fun currency(amount: Double, currency: String): String {
        return try {
            val nf = NumberFormat.getCurrencyInstance(Locale.US)
            nf.currency = Currency.getInstance(currency)
            val digits = if (currency in zeroDecimalCurrencies) 0 else 2
            nf.minimumFractionDigits = digits
            nf.maximumFractionDigits = digits
            nf.format(amount)
        } catch (e: Exception) {
            "$currency ${"%.2f".format(amount)}"
        }
    }

    fun dateRange(start: String, end: String): String {
        return try {
            val s = LocalDate.parse(start)
            val e = LocalDate.parse(end)
            val md = DateTimeFormatter.ofPattern("MMM d", Locale.US)
            val mdy = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
            if (s.year != e.year) "${s.format(mdy)} – ${e.format(mdy)}" else "${s.format(md)} – ${e.format(mdy)}"
        } catch (e: Exception) {
            "$start – $end"
        }
    }

    fun tripDays(start: String, end: String): Int = try {
        ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)).toInt() + 1
    } catch (e: Exception) {
        0
    }

    fun timestamp(iso: String): String = try {
        LocalDateTime.parse(iso.substringBefore("Z").substringBefore("+"))
            .format(DateTimeFormatter.ofPattern("MMM d, hh:mm a", Locale.US))
    } catch (e: Exception) {
        iso
    }

    private val currencyToCc = mapOf(
        "AED" to "AE", "ARS" to "AR", "AUD" to "AU", "BDT" to "BD", "BHD" to "BH", "BND" to "BN",
        "BRL" to "BR", "CAD" to "CA", "CHF" to "CH", "CLP" to "CL", "CNY" to "CN", "COP" to "CO",
        "CZK" to "CZ", "DKK" to "DK", "EGP" to "EG", "ETB" to "ET", "EUR" to "EU", "GBP" to "GB",
        "GHS" to "GH", "HKD" to "HK", "HUF" to "HU", "IDR" to "ID", "ILS" to "IL", "INR" to "IN",
        "ISK" to "IS", "JOD" to "JO", "JPY" to "JP", "KES" to "KE", "KHR" to "KH", "KRW" to "KR",
        "KWD" to "KW", "LAK" to "LA", "LKR" to "LK", "MMK" to "MM", "MOP" to "MO", "MXN" to "MX",
        "MYR" to "MY", "NGN" to "NG", "NOK" to "NO", "NPR" to "NP", "NZD" to "NZ", "OMR" to "OM",
        "PEN" to "PE", "PHP" to "PH", "PKR" to "PK", "PLN" to "PL", "QAR" to "QA", "RON" to "RO",
        "RUB" to "RU", "SAR" to "SA", "SEK" to "SE", "SGD" to "SG", "THB" to "TH", "TRY" to "TR",
        "TWD" to "TW", "TZS" to "TZ", "UAH" to "UA", "UGX" to "UG", "USD" to "US", "VND" to "VN",
        "ZAR" to "ZA",
    )

    /** currency → flag emoji, ported from flag.ts. */
    fun currencyFlag(currency: String): String {
        val cc = currencyToCc[currency.uppercase()] ?: return ""
        if (cc == "EU") return "🇪🇺"
        return cc.uppercase().map { (it.code + 0x1F1A5) }.joinToString("") { String(Character.toChars(it)) }
    }
}
