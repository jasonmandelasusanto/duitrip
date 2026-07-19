package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryCurrencyTest {

    @Test
    fun countryCode_mapsToCurrency() {
        assertEquals("JPY", CountryCurrency.countryCodeToCurrency("JP"))
        assertEquals("SGD", CountryCurrency.countryCodeToCurrency("SG"))
        assertEquals("EUR", CountryCurrency.countryCodeToCurrency("DE"))
    }

    @Test
    fun countryCode_isCaseInsensitive() {
        assertEquals("JPY", CountryCurrency.countryCodeToCurrency("jp"))
    }

    @Test
    fun countryCode_unknownDefaultsToUsd() {
        assertEquals("USD", CountryCurrency.countryCodeToCurrency("ZZ"))
    }

    @Test
    fun resolveCurrency_matchesDestinationName() {
        assertEquals("JPY", CountryCurrency.resolveCurrency("Tokyo, Japan"))
        assertEquals("SGD", CountryCurrency.resolveCurrency("Singapore"))
        assertEquals("THB", CountryCurrency.resolveCurrency("Bangkok, Thailand"))
    }

    @Test
    fun resolveCurrency_unknownDefaultsToUsd() {
        assertEquals("USD", CountryCurrency.resolveCurrency("Somewhere Unknown"))
    }
}
