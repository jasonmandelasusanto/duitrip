package com.duitrip.app.data

import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Settlement
import com.duitrip.app.data.model.Trip
import com.duitrip.app.domain.ExpenseShares
import com.duitrip.app.domain.Money
import com.duitrip.app.domain.SettlementAmount
import com.duitrip.app.domain.SettlementCalculator
import com.duitrip.app.domain.ShareAmount
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// ── Settlement-plan view models (mirror the old /settlement JSON) ──────────────
data class TransferView(
    val fromUserId: String,
    val fromDisplayName: String,
    val fromIsGhost: Boolean,
    val toUserId: String,
    val toDisplayName: String,
    val toIsGhost: Boolean,
    val amountInDestinationCurrency: Double,
    val destinationCurrency: String,
    val amountInFromHomeCurrency: Double,
    val fromHomeCurrency: String,
    val amountInToHomeCurrency: Double,
    val toHomeCurrency: String,
)

data class PerMemberBalance(
    val userId: String,
    val displayName: String,
    val isGhost: Boolean,
    val totalPaid: Double,
    val totalOwed: Double,
    val balance: Double,
    val balanceInHomeCurrency: Double,
    val homeCurrency: String,
)

data class SettlementPlan(
    val transactions: List<TransferView>,
    val perMember: List<PerMemberBalance>,
    val totalExpenses: Double,
    val destinationCurrency: String,
    val stale: Boolean,
)

/** trips/{tripId}/settlements — recorded paybacks + the computed settle-up plan. */
class SettlementRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun settlements(tripId: String) =
        db.collection("trips").document(tripId).collection("settlements")

    fun observeSettlementsRaw(tripId: String): Flow<List<Settlement>> =
        settlements(tripId).snapshotsFlow()

    /** One-shot list used for local XLSX backup. */
    suspend fun getSettlements(tripId: String): List<Settlement> =
        settlements(tripId).get().await().documents.mapNotNull { it.toObject(Settlement::class.java) }

    /** Settlements with display names resolved from the trip, newest first. */
    fun observeSettlements(tripId: String, trip: Trip): Flow<List<Settlement>> =
        observeSettlementsRaw(tripId).map { list ->
            val byId = trip.members.associateBy { it.memberId }
            list.onEach { s ->
                s.fromDisplayName = byId[s.fromUserId]?.displayName ?: s.fromUserId
                s.toDisplayName = byId[s.toUserId]?.displayName ?: s.toUserId
            }.sortedByDescending { it.settledAt }
        }

    suspend fun recordSettlement(
        trip: Trip,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        note: String?,
    ): Settlement {
        val id = IdGen.settlement()
        val settlement = Settlement(
            settlementId = id,
            fromUserId = fromUserId,
            toUserId = toUserId,
            amountInDestinationCurrency = amount,
            destinationCurrency = trip.destinationCurrency,
            note = note,
            settledAt = Timestamp.now(),
            createdBy = fromUserId,
        )
        settlements(trip.tripId).document(id).set(settlement).await()
        return settlement
    }

    suspend fun updateSettlementNote(tripId: String, settlementId: String, note: String?) {
        settlements(tripId).document(settlementId).update("note", note).await()
    }

    suspend fun deleteSettlement(tripId: String, settlementId: String) {
        settlements(tripId).document(settlementId).delete().await()
    }

    /** Computes the settle-up plan (ported from get_settlement). Fetches live rates. */
    suspend fun computePlan(
        trip: Trip,
        expenses: List<Expense>,
        recorded: List<Settlement>,
    ): SettlementPlan {
        val dest = trip.destinationCurrency
        val shares = expenses.map { e ->
            ExpenseShares(e.paidBy, e.amountInDestinationCurrency, e.splits.map { ShareAmount(it.userId, it.amountInDestinationCurrency) })
        }
        val settlementAmounts = recorded.map { SettlementAmount(it.fromUserId, it.toUserId, it.amountInDestinationCurrency) }

        val balances = SettlementCalculator.calculateBalances(shares, settlementAmounts)
        val transfers = SettlementCalculator.simplifyDebts(balances)

        val byId = trip.members.associateBy { it.memberId }
        val homeCurrencies = trip.members.map { it.homeCurrency }.filter { it.isNotBlank() }.distinct()
        val rates = FrankfurterClient.fetchRates(dest, homeCurrencies)

        val transferViews = transfers.map { tx ->
            val fromM = byId[tx.from]
            val toM = byId[tx.to]
            val fromCur = fromM?.homeCurrency ?: dest
            val toCur = toM?.homeCurrency ?: dest
            TransferView(
                fromUserId = tx.from,
                fromDisplayName = fromM?.displayName ?: tx.from,
                fromIsGhost = tx.from.startsWith("ghost_"),
                toUserId = tx.to,
                toDisplayName = toM?.displayName ?: tx.to,
                toIsGhost = tx.to.startsWith("ghost_"),
                amountInDestinationCurrency = tx.amount,
                destinationCurrency = dest,
                amountInFromHomeCurrency = Money.round2(tx.amount * (rates[fromCur] ?: 1.0)),
                fromHomeCurrency = fromCur,
                amountInToHomeCurrency = Money.round2(tx.amount * (rates[toCur] ?: 1.0)),
                toHomeCurrency = toCur,
            )
        }

        val perMember = balances.entries.map { (uid, bal) ->
            val m = byId[uid]
            val homeCur = m?.homeCurrency ?: dest
            PerMemberBalance(
                userId = uid,
                displayName = m?.displayName ?: uid,
                isGhost = uid.startsWith("ghost_"),
                totalPaid = Money.round2(expenses.filter { it.paidBy == uid }.sumOf { it.amountInDestinationCurrency }),
                totalOwed = Money.round2(expenses.flatMap { it.splits }.filter { it.userId == uid }.sumOf { it.amountInDestinationCurrency }),
                balance = Money.round2(bal),
                balanceInHomeCurrency = Money.round2(bal * (rates[homeCur] ?: 1.0)),
                homeCurrency = homeCur,
            )
        }

        return SettlementPlan(
            transactions = transferViews,
            perMember = perMember,
            totalExpenses = Money.round2(expenses.sumOf { it.amountInDestinationCurrency }),
            destinationCurrency = dest,
            stale = FrankfurterClient.isStale(dest, homeCurrencies),
        )
    }
}
