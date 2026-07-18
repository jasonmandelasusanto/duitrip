package com.duitrip.app.domain

/** Ported 1:1 from backend/app/utils/country_currency.py. */
object CountryCurrency {

    val COUNTRY_TO_CURRENCY: Map<String, String> = mapOf(
        "AD" to "EUR", "AE" to "AED", "AF" to "AFN", "AG" to "XCD", "AL" to "ALL",
        "AM" to "AMD", "AO" to "AOA", "AR" to "ARS", "AT" to "EUR", "AU" to "AUD",
        "AZ" to "AZN", "BA" to "BAM", "BB" to "BBD", "BD" to "BDT", "BE" to "EUR",
        "BF" to "XOF", "BG" to "BGN", "BH" to "BHD", "BI" to "BIF", "BJ" to "XOF",
        "BN" to "BND", "BO" to "BOB", "BR" to "BRL", "BS" to "BSD", "BT" to "BTN",
        "BW" to "BWP", "BY" to "BYN", "BZ" to "BZD", "CA" to "CAD", "CD" to "CDF",
        "CF" to "XAF", "CG" to "XAF", "CH" to "CHF", "CI" to "XOF", "CL" to "CLP",
        "CM" to "XAF", "CN" to "CNY", "CO" to "COP", "CR" to "CRC", "CU" to "CUP",
        "CV" to "CVE", "CY" to "EUR", "CZ" to "CZK", "DE" to "EUR", "DJ" to "DJF",
        "DK" to "DKK", "DM" to "XCD", "DO" to "DOP", "DZ" to "DZD", "EC" to "USD",
        "EE" to "EUR", "EG" to "EGP", "ER" to "ERN", "ES" to "EUR", "ET" to "ETB",
        "FI" to "EUR", "FJ" to "FJD", "FR" to "EUR", "GA" to "XAF", "GB" to "GBP",
        "GD" to "XCD", "GE" to "GEL", "GH" to "GHS", "GM" to "GMD", "GN" to "GNF",
        "GQ" to "XAF", "GR" to "EUR", "GT" to "GTQ", "GW" to "XOF", "GY" to "GYD",
        "HN" to "HNL", "HR" to "EUR", "HT" to "HTG", "HU" to "HUF", "ID" to "IDR",
        "IE" to "EUR", "IL" to "ILS", "IN" to "INR", "IQ" to "IQD", "IR" to "IRR",
        "IS" to "ISK", "IT" to "EUR", "JM" to "JMD", "JO" to "JOD", "JP" to "JPY",
        "KE" to "KES", "KG" to "KGS", "KH" to "KHR", "KI" to "AUD", "KM" to "KMF",
        "KN" to "XCD", "KP" to "KPW", "KR" to "KRW", "KW" to "KWD", "KZ" to "KZT",
        "LA" to "LAK", "LB" to "LBP", "LC" to "XCD", "LI" to "CHF", "LK" to "LKR",
        "LR" to "LRD", "LS" to "LSL", "LT" to "EUR", "LU" to "EUR", "LV" to "EUR",
        "LY" to "LYD", "MA" to "MAD", "MC" to "EUR", "MD" to "MDL", "ME" to "EUR",
        "MG" to "MGA", "MH" to "USD", "MK" to "MKD", "ML" to "XOF", "MM" to "MMK",
        "MN" to "MNT", "MR" to "MRU", "MT" to "EUR", "MU" to "MUR", "MV" to "MVR",
        "MW" to "MWK", "MX" to "MXN", "MY" to "MYR", "MZ" to "MZN", "NA" to "NAD",
        "NE" to "XOF", "NG" to "NGN", "NI" to "NIO", "NL" to "EUR", "NO" to "NOK",
        "NP" to "NPR", "NR" to "AUD", "NZ" to "NZD", "OM" to "OMR", "PA" to "PAB",
        "PE" to "PEN", "PG" to "PGK", "PH" to "PHP", "PK" to "PKR", "PL" to "PLN",
        "PT" to "EUR", "PW" to "USD", "PY" to "PYG", "QA" to "QAR", "RO" to "RON",
        "RS" to "RSD", "RU" to "RUB", "RW" to "RWF", "SA" to "SAR", "SB" to "SBD",
        "SC" to "SCR", "SD" to "SDG", "SE" to "SEK", "SG" to "SGD", "SI" to "EUR",
        "SK" to "EUR", "SL" to "SLL", "SM" to "EUR", "SN" to "XOF", "SO" to "SOS",
        "SR" to "SRD", "SS" to "SSP", "ST" to "STN", "SV" to "USD", "SY" to "SYP",
        "SZ" to "SZL", "TD" to "XAF", "TG" to "XOF", "TH" to "THB", "TJ" to "TJS",
        "TL" to "USD", "TM" to "TMT", "TN" to "TND", "TO" to "TOP", "TR" to "TRY",
        "TT" to "TTD", "TV" to "AUD", "TZ" to "TZS", "UA" to "UAH", "UG" to "UGX",
        "US" to "USD", "UY" to "UYU", "UZ" to "UZS", "VA" to "EUR", "VC" to "XCD",
        "VE" to "VES", "VN" to "VND", "VU" to "VUV", "WS" to "WST", "YE" to "YER",
        "ZA" to "ZAR", "ZM" to "ZMW", "ZW" to "ZWL",
        "HK" to "HKD", "MO" to "MOP", "TW" to "TWD", "PS" to "ILS",
    )

    fun countryCodeToCurrency(countryCode: String): String =
        COUNTRY_TO_CURRENCY[countryCode.uppercase()] ?: "USD"

    /**
     * Best-effort destination-name → currency, ported from `_resolve_currency`
     * in backend/app/routers/trips.py.
     */
    fun resolveCurrency(destination: String): String {
        val destLower = destination.lowercase()
        val nameToCode = mapOf(
            "indonesia" to "ID", "singapore" to "SG", "thailand" to "TH", "malaysia" to "MY",
            "japan" to "JP", "korea" to "KR", "vietnam" to "VN", "philippines" to "PH",
            "australia" to "AU", "united states" to "US", "usa" to "US", "uk" to "GB",
            "united kingdom" to "GB", "germany" to "DE", "france" to "FR", "italy" to "IT",
            "spain" to "ES", "india" to "IN", "china" to "CN", "hong kong" to "HK",
            "taiwan" to "TW", "new zealand" to "NZ", "canada" to "CA",
        )
        for ((name, code) in nameToCode) {
            if (destLower.contains(name)) return countryCodeToCurrency(code)
        }
        return "USD"
    }
}
