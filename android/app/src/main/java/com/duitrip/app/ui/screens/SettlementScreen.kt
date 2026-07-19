package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.data.ExpenseRepository
import com.duitrip.app.data.SettlementPlan
import com.duitrip.app.data.SettlementRepository
import com.duitrip.app.data.TransferView
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Settlement
import com.duitrip.app.data.model.Trip
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.Success
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettlementViewModel(
    tripRepo: TripRepository,
    expenseRepo: ExpenseRepository,
    private val settlementRepo: SettlementRepository,
    private val tripId: String,
) : ViewModel() {
    val trip: StateFlow<Trip?> = tripRepo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val expenses: StateFlow<List<Expense>> = expenseRepo.observeExpenses(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settlements: StateFlow<List<Settlement>> = settlementRepo.observeSettlementsRaw(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun computePlan(t: Trip, e: List<Expense>, s: List<Settlement>): SettlementPlan =
        settlementRepo.computePlan(t, e, s)

    fun record(t: Trip, from: String, to: String, amount: Double, note: String?) {
        viewModelScope.launch { settlementRepo.recordSettlement(t, from, to, amount, note?.takeIf { it.isNotBlank() }) }
    }

    fun delete(id: String) {
        viewModelScope.launch { settlementRepo.deleteSettlement(tripId, id) }
    }
}

@Composable
fun SettlementScreen(tripId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val uid = LocalCurrentUser.current?.uid ?: return
    val vm: SettlementViewModel = viewModel(
        factory = VMFactory { SettlementViewModel(container.tripRepository, container.expenseRepository, container.settlementRepository, tripId) },
    )
    val trip by vm.trip.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val settlements by vm.settlements.collectAsStateWithLifecycle()

    var plan by remember { mutableStateOf<SettlementPlan?>(null) }
    var noteFor by remember { mutableStateOf<TransferView?>(null) }
    LaunchedEffect(trip, expenses, settlements) {
        val t = trip ?: return@LaunchedEffect
        plan = vm.computePlan(t, expenses, settlements)
    }

    ScreenScaffold(title = "Settle up", onBack = onBack) { pad ->
        val p = plan
        val t = trip
        if (p == null || t == null) { CenteredMessage("", loading = true); return@ScreenScaffold }

        val mine = p.perMember.firstOrNull { it.userId == uid }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Your balance card (parity with the PWA balance view)
            if (mine != null) {
                item {
                    SurfaceCard {
                        Text("Your balance", color = TextSecondary)
                        Text(
                            Format.currency(mine.balance, p.destinationCurrency),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mine.balance >= 0) Success else Danger,
                        )
                        Text(
                            "${Format.currency(mine.balanceInHomeCurrency, mine.homeCurrency)} · paid ${Format.currency(mine.totalPaid, p.destinationCurrency)} · owed ${Format.currency(mine.totalOwed, p.destinationCurrency)}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                Text("Total: ${Format.currency(p.totalExpenses, p.destinationCurrency)}", color = TextSecondary)
                if (p.stale) Text("Rates may be stale", color = TextSecondary)
            }
            item { Text("Who pays whom", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            if (p.transactions.isEmpty()) {
                item { Text("All settled up 🎉", color = Success) }
            } else {
                items(p.transactions) { tx ->
                    SurfaceCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${tx.fromDisplayName} → ${tx.toDisplayName}", color = TextPrimary)
                            Text(Format.currency(tx.amountInDestinationCurrency, tx.destinationCurrency), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${tx.fromDisplayName}: ${Format.currency(tx.amountInFromHomeCurrency, tx.fromHomeCurrency)}",
                            color = TextSecondary,
                        )
                        TextButton(onClick = { noteFor = tx }) { Text("Mark as paid") }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)); Text("Recorded settlements", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            val byId = t.members.associateBy { it.memberId }
            items(settlements) { s ->
                SurfaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${byId[s.fromUserId]?.displayName ?: s.fromUserId} → ${byId[s.toUserId]?.displayName ?: s.toUserId}",
                            color = TextPrimary,
                        )
                        Text(Format.currency(s.amountInDestinationCurrency, s.destinationCurrency), color = TextPrimary)
                    }
                    s.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text("“$it”", color = TextSecondary, fontSize = 13.sp)
                    }
                    s.settledAt?.let {
                        Text(Format.timestamp(it.toDate().toInstant().toString()), color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.delete(s.settlementId) }) { Text("Undo") }
                }
            }
        }
    }

    // Record dialog with optional note (parity with the PWA)
    noteFor?.let { tx ->
        var note by remember(tx) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Record payment") },
            text = {
                Column {
                    Text(
                        "${tx.fromDisplayName} pays ${tx.toDisplayName} ${Format.currency(tx.amountInDestinationCurrency, tx.destinationCurrency)}",
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    DField(note, { note = it }, "Note (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    trip?.let { vm.record(it, tx.fromUserId, tx.toUserId, tx.amountInDestinationCurrency, note) }
                    noteFor = null
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Cancel") } },
        )
    }
}
