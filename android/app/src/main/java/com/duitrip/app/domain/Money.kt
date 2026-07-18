package com.duitrip.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

/**
 * Money helpers ported 1:1 from backend/app/services/currency.py.
 *
 * [round2] uses HALF_EVEN (banker's rounding) to match Python's built-in `round`,
 * so split reconciliation produces identical results to the old backend.
 */
object Money {

    /** Python: math.floor(value * 100) / 100 */
    fun floor2(value: Double): Double = floor(value * 100.0) / 100.0

    /** Python: round(value, 2) — round half to even. */
    fun round2(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()

    /** round(value, n) — used for derived percentages (4 dp). */
    fun roundN(value: Double, scale: Int): Double =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).toDouble()
}
