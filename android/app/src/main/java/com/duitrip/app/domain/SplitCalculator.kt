package com.duitrip.app.domain

import kotlin.math.abs

/** A computed share of an expense, before home-currency derivation. */
data class RawSplit(
    val userId: String,
    val amountInDestinationCurrency: Double,
    val percentage: Double? = null,
)

data class PercentageInput(val userId: String, val percentage: Double)

data class ExactInput(
    val userId: String,
    val exactAmount: Double,
    val exactCurrency: String,
)

/**
 * Split math ported 1:1 from backend/app/services/currency.py.
 * Any leftover rounding remainder is assigned to the payer, exactly as the backend did.
 */
object SplitCalculator {

    /** calculate_equal_splits */
    fun equal(total: Double, memberIds: List<String>, payerId: String): List<RawSplit> {
        val n = memberIds.size
        val base = Money.floor2(total / n)
        val remainder = Money.round2(total - base * n)
        return memberIds.map { uid ->
            val amt = if (uid == payerId) base + remainder else base
            RawSplit(uid, Money.round2(amt))
        }
    }

    /** calculate_percentage_splits */
    fun percentage(total: Double, inputs: List<PercentageInput>, payerId: String): List<RawSplit> {
        val totalPct = inputs.sumOf { it.percentage }
        if (abs(totalPct - 100.0) > 0.01) {
            throw IllegalArgumentException("Percentages must sum to 100, got $totalPct")
        }
        var running = 0.0
        val splits = inputs.map { s ->
            val amt = Money.floor2((s.percentage / 100.0) * total)
            running += amt
            RawSplit(s.userId, amt, s.percentage)
        }.toMutableList()

        val remainder = Money.round2(total - running)
        for (i in splits.indices) {
            if (splits[i].userId == payerId) {
                splits[i] = splits[i].copy(
                    amountInDestinationCurrency = Money.round2(splits[i].amountInDestinationCurrency + remainder),
                )
                break
            }
        }
        return splits
    }

    /**
     * calculate_exact_splits.
     * [rates] are destination-per-unit values keyed by currency (rates[dest] == 1.0),
     * matching the frankfurter snapshot stored on each expense.
     */
    fun exact(
        totalDest: Double,
        inputs: List<ExactInput>,
        rates: Map<String, Double>,
        destCurrency: String,
        payerId: String,
    ): List<RawSplit> {
        var running = 0.0
        val splits = inputs.map { s ->
            val amtDest = if (s.exactCurrency == destCurrency) {
                s.exactAmount
            } else {
                val rateDestPerExact = (rates[destCurrency] ?: 1.0) / (rates[s.exactCurrency] ?: 1.0)
                Money.floor2(s.exactAmount * rateDestPerExact)
            }
            running += amtDest
            RawSplit(s.userId, Money.round2(amtDest))
        }.toMutableList()

        val remainder = Money.round2(totalDest - running)
        if (abs(remainder) > 0.02) {
            throw IllegalArgumentException("Exact split amounts don't match total (diff=$remainder)")
        }
        for (i in splits.indices) {
            if (splits[i].userId == payerId) {
                splits[i] = splits[i].copy(
                    amountInDestinationCurrency = Money.round2(splits[i].amountInDestinationCurrency + remainder),
                )
                break
            }
        }
        return splits
    }
}
