package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.data.ExpenseRepository
import com.duitrip.app.data.SettlementRepository
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Settlement
import com.duitrip.app.data.model.Trip
import com.duitrip.app.domain.Categories
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.EmptyState
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.BgBase
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TripDetailViewModel(
    tripRepo: TripRepository,
    expenseRepo: ExpenseRepository,
    settlementRepo: SettlementRepository,
    tripId: String,
) : ViewModel() {
    val trip: StateFlow<Trip?> =
        tripRepo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val expenses: StateFlow<List<Expense>?> =
        expenseRepo.observeExpenses(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settlements: StateFlow<List<Settlement>> =
        settlementRepo.observeSettlementsRaw(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun TripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onMembers: () -> Unit,
    onSettlement: () -> Unit,
    onAnalytics: () -> Unit,
) {
    val container = LocalContainer.current
    val vm: TripDetailViewModel = viewModel(
        factory = VMFactory {
            TripDetailViewModel(container.tripRepository, container.expenseRepository, container.settlementRepository, tripId)
        },
    )
    val trip by vm.trip.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val settlements by vm.settlements.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = trip?.name ?: "Trip",
        onBack = onBack,
    ) { pad ->
        val t = trip
        if (t == null) {
            CenteredMessage("", loading = true)
            return@ScreenScaffold
        }
        Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize()) {
            // Header + quick actions
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${Format.currencyFlag(t.destinationCurrency)}  ${t.destination}",
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(Format.dateRange(t.startDate, t.endDate), color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onMembers, label = { Text("Members") })
                    AssistChip(onClick = onSettlement, label = { Text("Settle up") })
                    AssistChip(onClick = onAnalytics, label = { Text("Analytics") })
                }
            }

            val list = expenses
            when {
                list == null -> CenteredMessage("", loading = true)
                list.isEmpty() -> EmptyState("No expenses yet", "Tap + to add the first one.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list) { exp ->
                        ExpenseRow(exp, t, settlements, onClick = { onEditExpense(exp.expenseId) })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddExpense,
            containerColor = Teal,
            contentColor = BgBase,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add expense")
        }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, trip: Trip, settlements: List<Settlement>, onClick: () -> Unit) {
    val payerName = trip.members.firstOrNull { it.memberId == expense.paidBy }?.displayName ?: expense.paidBy
    val statuses = ExpenseRepository.memberStatuses(expense, trip, settlements)
    SurfaceCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${Categories.emojiFor(expense.category)}  ${expense.description}",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                Format.currency(expense.amountInDestinationCurrency, expense.destinationCurrency),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Paid by $payerName · ${statuses.size} ${if (statuses.size == 1) "person" else "people"}", color = TextSecondary, fontSize = 13.sp)
    }
}
