package com.duitrip.app.data

import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.MemberStatus
import com.duitrip.app.data.model.Settlement
import com.duitrip.app.data.model.SplitEntry
import com.duitrip.app.data.model.Trip
import com.duitrip.app.domain.ExpenseBuilder
import com.duitrip.app.domain.ExpenseDraft
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** trips/{tripId}/expenses — CRUD + rate-locking, ported from the expenses router. */
class ExpenseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun expenses(tripId: String) =
        db.collection("trips").document(tripId).collection("expenses")

    fun observeExpenses(tripId: String): Flow<List<Expense>> =
        expenses(tripId).orderBy("createdAt", Query.Direction.DESCENDING).snapshotsFlow()

    suspend fun getExpense(tripId: String, expenseId: String): Expense? =
        expenses(tripId).document(expenseId).get().await().toObject(Expense::class.java)

    suspend fun addExpense(trip: Trip, draft: ExpenseDraft, createdBy: String): Expense {
        val expenseId = IdGen.expense()
        val expense = buildExpense(trip, draft, expenseId, createdBy, createdAt = Timestamp.now())
        expenses(trip.tripId).document(expenseId).set(expense).await()
        db.collection("trips").document(trip.tripId)
            .update("updatedAt", Timestamp.now()).await()
        return expense
    }

    /** Rebuilds the expense (recomputing splits + rate snapshot) while preserving origin. */
    suspend fun updateExpense(
        trip: Trip,
        expenseId: String,
        draft: ExpenseDraft,
        originalCreatedBy: String,
        originalCreatedAt: Timestamp?,
    ): Expense {
        val expense = buildExpense(trip, draft, expenseId, originalCreatedBy, createdAt = originalCreatedAt)
        expenses(trip.tripId).document(expenseId).set(expense).await()
        return expense
    }

    suspend fun deleteExpense(tripId: String, expenseId: String) {
        expenses(tripId).document(expenseId).delete().await()
    }

    private suspend fun buildExpense(
        trip: Trip,
        draft: ExpenseDraft,
        expenseId: String,
        createdBy: String,
        createdAt: Timestamp?,
    ): Expense {
        val dest = trip.destinationCurrency
        val memberIds = trip.members.mapNotNull { it.memberId }
        val homeCurrencies = trip.members.mapNotNull { m -> m.memberId?.let { it to m.homeCurrency } }.toMap()

        val toFetch = ExpenseBuilder.currenciesToFetch(draft.originalCurrency, dest, homeCurrencies.values)
        val rates = FrankfurterClient.fetchRates(dest, toFetch)
        val computed = ExpenseBuilder.build(draft, dest, memberIds, homeCurrencies, rates)

        val now = Timestamp.now()
        return Expense(
            expenseId = expenseId,
            description = draft.description,
            category = draft.category,
            originalAmount = draft.originalAmount,
            originalCurrency = draft.originalCurrency,
            destinationCurrency = dest,
            amountInDestinationCurrency = computed.amountInDestinationCurrency,
            exchangeRateUsed = computed.exchangeRateUsed,
            exchangeRateTimestamp = Instant.now().toString(),
            exchangeRates = computed.exchangeRates,
            splitMode = computed.splitMode,
            paidBy = draft.paidBy,
            splits = computed.splits.map {
                SplitEntry(
                    userId = it.userId,
                    percentage = it.percentage,
                    amountInDestinationCurrency = it.amountInDestinationCurrency,
                    amountInHomeCurrency = it.amountInHomeCurrency,
                    homeCurrency = it.homeCurrency,
                )
            },
            receiptUrl = null,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = now,
        )
    }

    companion object {
        /**
         * Per-member status for an expense row, ported from list_expenses in the
         * expenses router. A member is "settled" if any settlement references them.
         */
        fun memberStatuses(
            expense: Expense,
            trip: Trip,
            settlements: List<Settlement>,
        ): List<MemberStatus> {
            val memberById = trip.members.associateBy { it.memberId }
            return expense.splits.map { sp ->
                val uid = sp.userId
                val isPayer = uid == expense.paidBy
                val settled = settlements.any { it.fromUserId == uid || it.toUserId == uid }
                val status = when {
                    isPayer -> "paid"
                    settled -> "settled"
                    else -> "outstanding"
                }
                MemberStatus(
                    userId = uid,
                    displayName = memberById[uid]?.displayName ?: uid,
                    isGhost = uid.startsWith("ghost_"),
                    isPayer = isPayer,
                    amountInDestinationCurrency = sp.amountInDestinationCurrency,
                    amountInHomeCurrency = sp.amountInHomeCurrency,
                    homeCurrency = sp.homeCurrency,
                    status = status,
                )
            }
        }
    }
}
