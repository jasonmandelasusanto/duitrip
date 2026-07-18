package com.duitrip.app.domain

/** Input shape for analytics — decoupled from Firestore models. */
data class AnalyticsExpense(
    val expenseId: String,
    val description: String,
    val category: String,
    val amountInDestinationCurrency: Double,
    val paidBy: String,
    val dateStr: String, // "yyyy-MM-dd", derived from createdAt
    val splits: List<ShareAmount>,
)

data class MemberRef(
    val id: String, // userId or ghostId
    val displayName: String,
    val homeCurrency: String,
    val isGhost: Boolean,
)

// ── Result types (mirror the old /analytics JSON) ──────────────────────────────
data class CategorySlice(val category: String, val emoji: String, val amount: Double, val percentage: Double)
data class DaySlice(val date: String, val amount: Double, val expenseCount: Int)
data class MemberSlice(val userId: String, val displayName: String, val isGhost: Boolean, val totalPaid: Double, val percentage: Double)
data class TimelineEntry(val date: String, val expenseId: String, val description: String, val myShare: Double, val category: String, val emoji: String)

data class GroupAnalytics(
    val totalSpend: Double,
    val totalSpendPerMember: Double,
    val byCategory: List<CategorySlice>,
    val byDay: List<DaySlice>,
    val byMember: List<MemberSlice>,
)

data class VsGroupAverage(val myShare: Double, val groupAverage: Double, val difference: Double, val percentageDifference: Double)

data class IndividualAnalytics(
    val userId: String,
    val displayName: String,
    val totalShare: Double,
    val homeCurrency: String,
    val byCategory: List<CategorySlice>,
    val vsGroupAverage: VsGroupAverage,
    val timeline: List<TimelineEntry>,
)

data class TripAnalytics(
    val destinationCurrency: String,
    val dateFrom: String?,
    val dateTo: String?,
    val group: GroupAnalytics,
    val individual: IndividualAnalytics,
)

/** Ported 1:1 from backend/app/routers/analytics.py. */
object Analytics {

    fun compute(
        expenses: List<AnalyticsExpense>,
        members: List<MemberRef>,
        realMemberCount: Int,
        destCurrency: String,
        currentUid: String,
        startDate: String?,
        endDate: String?,
    ): TripAnalytics {
        val memberMap = members.associateBy { it.id }
        val total = expenses.sumOf { it.amountInDestinationCurrency }
        val n = if (realMemberCount > 0) realMemberCount else 1

        val byCategory = LinkedHashMap<String, Double>()
        val byDay = LinkedHashMap<String, Pair<Double, Int>>() // amount, count
        val byMemberPaid = LinkedHashMap<String, Double>()

        for (exp in expenses) {
            byCategory[exp.category] = (byCategory[exp.category] ?: 0.0) + exp.amountInDestinationCurrency
            val (amt, cnt) = byDay[exp.dateStr] ?: (0.0 to 0)
            byDay[exp.dateStr] = (amt + exp.amountInDestinationCurrency) to (cnt + 1)
            byMemberPaid[exp.paidBy] = (byMemberPaid[exp.paidBy] ?: 0.0) + exp.amountInDestinationCurrency
        }

        val categoryList = byCategory.entries
            .sortedByDescending { it.value }
            .map { (cat, amt) ->
                CategorySlice(cat, Categories.emojiFor(cat), Money.round2(amt), pct(amt, total))
            }
        val dayList = byDay.entries
            .sortedBy { it.key }
            .map { (d, v) -> DaySlice(d, Money.round2(v.first), v.second) }
        val memberList = byMemberPaid.entries
            .sortedByDescending { it.value }
            .map { (uid, amt) ->
                val m = memberMap[uid]
                MemberSlice(uid, m?.displayName ?: uid, uid.startsWith("ghost_"), Money.round2(amt), pct(amt, total))
            }

        // Individual
        var myTotal = 0.0
        val myByCategory = LinkedHashMap<String, Double>()
        val timeline = mutableListOf<TimelineEntry>()
        for (exp in expenses) {
            for (sp in exp.splits) {
                if (sp.userId == currentUid) {
                    val share = sp.amountInDestinationCurrency
                    myTotal += share
                    myByCategory[exp.category] = (myByCategory[exp.category] ?: 0.0) + share
                    timeline.add(
                        TimelineEntry(
                            date = exp.dateStr,
                            expenseId = exp.expenseId,
                            description = exp.description,
                            myShare = Money.round2(share),
                            category = exp.category,
                            emoji = Categories.emojiFor(exp.category),
                        ),
                    )
                }
            }
        }
        timeline.sortBy { it.date }

        val myCategoryList = myByCategory.entries
            .sortedByDescending { it.value }
            .map { (cat, amt) ->
                CategorySlice(cat, Categories.emojiFor(cat), Money.round2(amt), pct(amt, myTotal))
            }

        val groupAvg = if (n > 0) Money.round2(total / n) else 0.0
        val myInfo = memberMap[currentUid]

        return TripAnalytics(
            destinationCurrency = destCurrency,
            dateFrom = startDate,
            dateTo = endDate,
            group = GroupAnalytics(
                totalSpend = Money.round2(total),
                totalSpendPerMember = Money.round2(total / n),
                byCategory = categoryList,
                byDay = dayList,
                byMember = memberList,
            ),
            individual = IndividualAnalytics(
                userId = currentUid,
                displayName = myInfo?.displayName ?: currentUid,
                totalShare = Money.round2(myTotal),
                homeCurrency = myInfo?.homeCurrency ?: destCurrency,
                byCategory = myCategoryList,
                vsGroupAverage = VsGroupAverage(
                    myShare = Money.round2(myTotal),
                    groupAverage = groupAvg,
                    difference = Money.round2(myTotal - groupAvg),
                    percentageDifference = if (groupAvg != 0.0) Money.round2((myTotal - groupAvg) / groupAvg * 100) else 0.0,
                ),
                timeline = timeline,
            ),
        )
    }

    private fun pct(amt: Double, total: Double): Double =
        if (total != 0.0) Money.round2(amt / total * 100) else 0.0
}
