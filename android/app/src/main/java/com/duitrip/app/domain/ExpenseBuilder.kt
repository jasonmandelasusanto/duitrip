package com.duitrip.app.domain

/** Per-participant input for an expense (mirrors the old ExpenseCreate.splits). */
data class SplitInput(
    val userId: String,
    val percentage: Double? = null,
    val exactAmount: Double? = null,
    val exactCurrency: String? = null,
)

data class ExpenseDraft(
    val description: String,
    val category: String,
    val originalAmount: Double,
    val originalCurrency: String,
    val paidBy: String,
    val splitMode: String, // "equal" | "percentage" | "exact"
    val splits: List<SplitInput>,
)

data class FinalSplit(
    val userId: String,
    val percentage: Double,
    val amountInDestinationCurrency: Double,
    val amountInHomeCurrency: Double,
    val homeCurrency: String,
)

data class ComputedExpense(
    val amountInDestinationCurrency: Double,
    val exchangeRateUsed: Double,
    val exchangeRates: Map<String, Double>,
    val splitMode: String,
    val splits: List<FinalSplit>,
)

/**
 * Ported 1:1 from `_build_expense` in backend/app/routers/expenses.py.
 *
 * [rates] is a frankfurter snapshot with base = destination currency, so
 * rates[currency] = how many `currency` per 1 destination unit (rates[dest] == 1.0).
 * The caller fetches it (see FrankfurterClient) and stores it on the expense so the
 * rate stays locked at record time.
 */
object ExpenseBuilder {

    fun build(
        draft: ExpenseDraft,
        destCurrency: String,
        memberIds: List<String>, // userId or ghostId for every member
        homeCurrencies: Map<String, String>, // memberId -> homeCurrency
        rates: Map<String, Double>,
    ): ComputedExpense {
        val fullRates = rates.toMutableMap().apply { put(destCurrency, 1.0) }

        val totalDest: Double
        val rateUsed: Double
        if (draft.originalCurrency == destCurrency) {
            totalDest = draft.originalAmount
            rateUsed = 1.0
        } else {
            val origRate = fullRates[draft.originalCurrency] ?: 1.0
            rateUsed = if (origRate != 0.0) 1.0 / origRate else 1.0
            totalDest = Money.round2(draft.originalAmount * rateUsed)
        }

        val splitIds = if (draft.splits.isNotEmpty()) draft.splits.map { it.userId } else memberIds

        val rawSplits: List<RawSplit> = when (draft.splitMode) {
            "percentage" -> SplitCalculator.percentage(
                totalDest,
                draft.splits.map { PercentageInput(it.userId, it.percentage ?: 0.0) },
                draft.paidBy,
            )
            "exact" -> SplitCalculator.exact(
                totalDest,
                draft.splits.map { ExactInput(it.userId, it.exactAmount ?: 0.0, it.exactCurrency ?: destCurrency) },
                fullRates,
                destCurrency,
                draft.paidBy,
            )
            else -> SplitCalculator.equal(totalDest, splitIds, draft.paidBy)
        }

        val finalSplits = rawSplits.map { sp ->
            val homeCurrency = homeCurrencies[sp.userId] ?: destCurrency
            val homeRate = if (homeCurrency != destCurrency) (fullRates[homeCurrency] ?: 1.0) else 1.0
            val amtDest = sp.amountInDestinationCurrency
            val amtHome = Money.round2(amtDest * homeRate)
            val pct = sp.percentage ?: if (totalDest != 0.0) Money.roundN(amtDest / totalDest * 100, 4) else 0.0
            FinalSplit(
                userId = sp.userId,
                percentage = pct,
                amountInDestinationCurrency = amtDest,
                amountInHomeCurrency = amtHome,
                homeCurrency = homeCurrency,
            )
        }

        return ComputedExpense(
            amountInDestinationCurrency = totalDest,
            exchangeRateUsed = rateUsed,
            exchangeRates = fullRates,
            splitMode = draft.splitMode,
            splits = finalSplits,
        )
    }

    /** All currencies needed for an expense's rate snapshot (matches _build_expense). */
    fun currenciesToFetch(
        originalCurrency: String,
        destCurrency: String,
        homeCurrencies: Collection<String>,
    ): List<String> =
        (homeCurrencies.toSet() + originalCurrency + destCurrency)
            .filter { it != destCurrency }
            .distinct()
}
