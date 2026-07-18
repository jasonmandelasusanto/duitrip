package com.duitrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.data.ExpenseRepository
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Trip
import com.duitrip.app.domain.Analytics
import com.duitrip.app.domain.AnalyticsExpense
import com.duitrip.app.domain.CategorySlice
import com.duitrip.app.domain.MemberRef
import com.duitrip.app.domain.ShareAmount
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.BgBorder
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

class AnalyticsViewModel(tripRepo: TripRepository, expenseRepo: ExpenseRepository, tripId: String) : ViewModel() {
    val trip: StateFlow<Trip?> = tripRepo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val expenses: StateFlow<List<Expense>?> = expenseRepo.observeExpenses(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun AnalyticsScreen(tripId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val uid = LocalCurrentUser.current?.uid ?: return
    val vm: AnalyticsViewModel = viewModel(factory = VMFactory { AnalyticsViewModel(container.tripRepository, container.expenseRepository, tripId) })
    val trip by vm.trip.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Analytics", onBack = onBack) { pad ->
        val t = trip
        val exp = expenses
        if (t == null || exp == null) { CenteredMessage("", loading = true); return@ScreenScaffold }

        val analytics = remember(t, exp) {
            val zone = ZoneId.systemDefault()
            val analyticsExpenses = exp.map { e ->
                val date = e.createdAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()?.toString() ?: ""
                AnalyticsExpense(
                    expenseId = e.expenseId,
                    description = e.description,
                    category = e.category,
                    amountInDestinationCurrency = e.amountInDestinationCurrency,
                    paidBy = e.paidBy,
                    dateStr = date,
                    splits = e.splits.map { ShareAmount(it.userId, it.amountInDestinationCurrency) },
                )
            }
            val members = t.members.mapNotNull { m -> m.memberId?.let { MemberRef(it, m.displayName, m.homeCurrency, m.isGhost) } }
            Analytics.compute(
                expenses = analyticsExpenses,
                members = members,
                realMemberCount = t.realMembers.size,
                destCurrency = t.destinationCurrency,
                currentUid = uid,
                startDate = t.startDate,
                endDate = t.endDate,
            )
        }
        val cur = t.destinationCurrency

        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SurfaceCard {
                    Text("Total spend", color = TextSecondary)
                    Text(Format.currency(analytics.group.totalSpend, cur), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${Format.currency(analytics.group.totalSpendPerMember, cur)} per member", color = TextSecondary)
                }
            }
            item {
                SurfaceCard {
                    Text("Your share", color = TextSecondary)
                    Text(Format.currency(analytics.individual.totalShare, cur), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    val diff = analytics.individual.vsGroupAverage.difference
                    Text(
                        if (diff >= 0) "+${Format.currency(diff, cur)} vs group average" else "${Format.currency(diff, cur)} vs group average",
                        color = TextSecondary,
                    )
                }
            }
            item { Text("By category", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            items(analytics.group.byCategory) { slice -> CategoryBar(slice, cur) }

            item { Spacer(Modifier.height(8.dp)); Text("By member", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            items(analytics.group.byMember) { m ->
                SurfaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.displayName, color = TextPrimary)
                        Text("${Format.currency(m.totalPaid, cur)}  (${m.percentage}%)", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBar(slice: CategorySlice, currency: String) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${slice.emoji}  ${slice.category}", color = TextPrimary)
            Text("${Format.currency(slice.amount, currency)}  (${slice.percentage}%)", color = TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(BgBorder),
        ) {
            Box(
                Modifier.fillMaxWidth((slice.percentage / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp).clip(RoundedCornerShape(4.dp)).background(Teal),
            )
        }
    }
}
