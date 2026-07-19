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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.EmptyState
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.BgBase
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.Success
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripDetailViewModel(
    private val tripRepo: TripRepository,
    private val expenseRepo: ExpenseRepository,
    settlementRepo: SettlementRepository,
    private val tripId: String,
) : ViewModel() {
    val trip: StateFlow<Trip?> =
        tripRepo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val expenses: StateFlow<List<Expense>?> =
        expenseRepo.observeExpenses(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settlements: StateFlow<List<Settlement>> =
        settlementRepo.observeSettlementsRaw(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteExpense(expenseId: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { expenseRepo.deleteExpense(tripId, expenseId) } catch (e: Exception) { onError(e.localizedMessage ?: "Delete failed") }
        }
    }

    fun updateTrip(name: String, destination: String, start: String, end: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { tripRepo.updateTrip(tripId, name, destination, start, end) } catch (e: Exception) { onError(e.localizedMessage ?: "Update failed") }
        }
    }

    fun archive(onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { tripRepo.archiveTrip(tripId); onDone() } catch (e: Exception) { onError(e.localizedMessage ?: "Archive failed") }
        }
    }

    fun deleteTrip(onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { tripRepo.deleteTrip(tripId); onDone() } catch (e: Exception) { onError(e.localizedMessage ?: "Delete failed") }
        }
    }
}

/** Mirrors the old backend rule: creator may delete within 24h; owner anytime. */
private fun canDelete(expense: Expense, trip: Trip, uid: String): Boolean {
    if (trip.createdBy == uid) return true
    if (expense.createdBy != uid) return false
    val created = expense.createdAt ?: return true
    val ageSec = Timestamp.now().seconds - created.seconds
    return ageSec <= 86400
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
    val uid = LocalCurrentUser.current?.uid ?: return
    val vm: TripDetailViewModel = viewModel(
        factory = VMFactory {
            TripDetailViewModel(container.tripRepository, container.expenseRepository, container.settlementRepository, tripId)
        },
    )
    val trip by vm.trip.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val settlements by vm.settlements.collectAsStateWithLifecycle()

    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var expandedExpense by remember { mutableStateOf<String?>(null) }
    var showEditTrip by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val t = trip
    ScreenScaffold(
        title = t?.name ?: "Trip",
        onBack = onBack,
        actions = {
            if (t != null && t.createdBy == uid) {
                IconButton(onClick = { showEditTrip = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit trip", tint = TextPrimary)
                }
                IconButton(onClick = { showArchiveConfirm = true }) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive trip", tint = TextPrimary)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete trip", tint = Danger)
                }
            }
        },
    ) { pad ->
        if (t == null) {
            CenteredMessage("", loading = true)
            return@ScreenScaffold
        }
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${Format.currencyFlag(t.destinationCurrency)}  ${t.destination}", color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(Format.dateRange(t.startDate, t.endDate), color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
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
                    else -> {
                        // Category filter chips (parity with the PWA's expense filters)
                        val cats = remember(list) { list.map { it.category }.distinct().sorted() }
                        if (cats.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") })
                                }
                                items(cats) { c ->
                                    FilterChip(
                                        selected = categoryFilter == c,
                                        onClick = { categoryFilter = if (categoryFilter == c) null else c },
                                        label = { Text("${Categories.emojiFor(c, t.customCategories.associate { it.name to it.emoji })} $c") },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        val filtered = if (categoryFilter == null) list else list.filter { it.category == categoryFilter }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { it.expenseId }) { exp ->
                                ExpenseRow(
                                    expense = exp,
                                    trip = t,
                                    settlements = settlements,
                                    expanded = expandedExpense == exp.expenseId,
                                    onClick = {
                                        expandedExpense = if (expandedExpense == exp.expenseId) null else exp.expenseId
                                    },
                                    onEdit = { onEditExpense(exp.expenseId) },
                                    canDelete = canDelete(exp, t, uid),
                                    onDelete = { vm.deleteExpense(exp.expenseId) { error = it } },
                                )
                            }
                        }
                    }
                }
            }
            error?.let {
                Text(it, color = Danger, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
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

    if (showEditTrip && t != null) {
        EditTripDialog(
            trip = t,
            onDismiss = { showEditTrip = false },
            onSave = { name, dest, start, end ->
                vm.updateTrip(name, dest, start, end) { error = it }
                showEditTrip = false
            },
        )
    }
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive trip?") },
            text = { Text("The trip is hidden from your dashboard. Data is kept.") },
            confirmButton = {
                TextButton(onClick = { showArchiveConfirm = false; vm.archive(onDone = onBack) { error = it } }) {
                    Text("Archive", color = Danger)
                }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") } },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete trip permanently?") },
            text = { Text("This permanently deletes the trip, all expenses, and settlement history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; vm.deleteTrip(onBack) { error = it } }) {
                    Text("Delete permanently", color = Danger)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditTripDialog(trip: Trip, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(trip.name) }
    var dest by remember { mutableStateOf(trip.destination) }
    var start by remember { mutableStateOf(trip.startDate) }
    var end by remember { mutableStateOf(trip.endDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit trip") },
        text = {
            Column {
                DField(name, { name = it }, "Trip name")
                Spacer(Modifier.height(8.dp))
                DField(dest, { dest = it }, "Destination")
                Spacer(Modifier.height(8.dp))
                DField(start, { start = it }, "Start (YYYY-MM-DD)")
                Spacer(Modifier.height(8.dp))
                DField(end, { end = it }, "End (YYYY-MM-DD)")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && dest.isNotBlank(), onClick = { onSave(name, dest, start, end) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    trip: Trip,
    settlements: List<Settlement>,
    expanded: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val payerName = trip.members.firstOrNull { it.memberId == expense.paidBy }?.displayName ?: expense.paidBy
    val statuses = ExpenseRepository.memberStatuses(expense, trip, settlements)
    val customEmoji = trip.customCategories.associate { it.name to it.emoji }
    SurfaceCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "${Categories.emojiFor(expense.category, customEmoji)}  ${expense.description}",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                Format.currency(expense.amountInDestinationCurrency, expense.destinationCurrency),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                softWrap = false,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Paid by $payerName · ${statuses.size} ${if (statuses.size == 1) "person" else "people"}" +
                if (expense.originalCurrency != expense.destinationCurrency)
                    " · ${Format.currency(expense.originalAmount, expense.originalCurrency)} @ rate locked" else "",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            // Per-member status rows (parity with the PWA expense card)
            statuses.forEach { st ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        st.displayName + if (st.isGhost) " 👻" else "",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            Format.currency(st.amountInDestinationCurrency, expense.destinationCurrency) +
                                "  (${Format.currency(st.amountInHomeCurrency, st.homeCurrency)})",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                        Text(
                            when (st.status) { "paid" -> "paid"; "settled" -> "settled"; else -> "owes" },
                            color = when (st.status) { "paid" -> Success; "settled" -> Teal; else -> Danger },
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                if (canDelete) TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
            }
        }
    }
}
