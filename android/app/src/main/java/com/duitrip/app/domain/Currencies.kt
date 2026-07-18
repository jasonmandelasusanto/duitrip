package com.duitrip.app.domain

/** Distinct currency codes for pickers, derived from the country→currency table. */
object Currencies {
    val ALL: List<String> = CountryCurrency.COUNTRY_TO_CURRENCY.values.distinct().sorted()

    /** A short, common shortlist surfaced at the top of pickers. */
    val COMMON: List<String> = listOf("USD", "EUR", "GBP", "JPY", "SGD", "AUD", "IDR", "MYR", "THB", "CNY")
}
