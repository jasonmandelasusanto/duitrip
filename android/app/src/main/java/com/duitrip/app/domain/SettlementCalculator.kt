package com.duitrip.app.domain

/** Minimal shapes the settlement math needs, decoupled from Firestore models. */
data class ExpenseShares(
    val paidBy: String,
    val amountInDestinationCurrency: Double,
    val splits: List<ShareAmount>,
)

data class ShareAmount(val userId: String, val amountInDestinationCurrency: Double)

data class SettlementAmount(
    val fromUserId: String,
    val toUserId: String,
    val amountInDestinationCurrency: Double,
)

/** A single suggested transfer from the greedy simplification. */
data class DebtTransfer(val from: String, val to: String, val amount: Double)

/**
 * Balance + debt-simplification engine ported 1:1 from
 * backend/app/services/settlement.py.
 */
object SettlementCalculator {

    /** Net balance per member: positive = owed money, negative = owes money. */
    fun calculateBalances(
        expenses: List<ExpenseShares>,
        settlements: List<SettlementAmount>,
    ): Map<String, Double> {
        val balances = LinkedHashMap<String, Double>()
        fun add(uid: String, delta: Double) {
            balances[uid] = (balances[uid] ?: 0.0) + delta
        }

        for (exp in expenses) {
            add(exp.paidBy, exp.amountInDestinationCurrency)
            for (split in exp.splits) {
                add(split.userId, -split.amountInDestinationCurrency)
            }
        }
        for (s in settlements) {
            add(s.fromUserId, s.amountInDestinationCurrency)
            add(s.toUserId, -s.amountInDestinationCurrency)
        }
        return balances
    }

    /** Greedy debt simplification — minimal list of transfers. */
    fun simplifyDebts(balances: Map<String, Double>): List<DebtTransfer> {
        val creditors = balances.entries
            .filter { it.value > 0.005 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()
        val debtors = balances.entries
            .filter { it.value < -0.005 }
            .map { it.key to -it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        val transactions = mutableListOf<DebtTransfer>()
        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (cUid, cAmt) = creditors[ci]
            val (dUid, dAmt) = debtors[di]
            val transfer = minOf(cAmt, dAmt)
            transactions.add(DebtTransfer(from = dUid, to = cUid, amount = Money.round2(transfer)))
            creditors[ci] = cUid to (cAmt - transfer)
            debtors[di] = dUid to (dAmt - transfer)
            if (creditors[ci].second < 0.005) ci++
            if (debtors[di].second < 0.005) di++
        }
        return transactions
    }
}
