package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.data.ExpenseRepository
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Trip
import com.duitrip.app.domain.Categories
import com.duitrip.app.domain.ExpenseDraft
import com.duitrip.app.domain.SplitInput
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.CurrencyField
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.FieldCard
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AddExpenseViewModel(
    private val tripRepo: TripRepository,
    private val expenseRepo: ExpenseRepository,
    private val tripId: String,
    private val expenseId: String?,
) : ViewModel() {
    val trip = MutableStateFlow<Trip?>(null)
    val existing = MutableStateFlow<Expense?>(null)

    init {
        viewModelScope.launch { trip.value = tripRepo.getTrip(tripId) }
        if (expenseId != null) {
            viewModelScope.launch { existing.value = expenseRepo.getExpense(tripId, expenseId) }
        }
    }

    suspend fun submit(draft: ExpenseDraft, createdBy: String) {
        val t = trip.value ?: throw IllegalStateException("Trip not loaded")
        val existingExp = existing.value
        if (existingExp != null) {
            expenseRepo.updateExpense(t, existingExp.expenseId, draft, existingExp.createdBy, existingExp.createdAt)
        } else {
            expenseRepo.addExpense(t, draft, createdBy)
        }
    }
}

@Composable
fun AddExpenseScreen(tripId: String, expenseId: String?, onBack: () -> Unit) {
    val container = LocalContainer.current
    val currentUid = LocalCurrentUser.current?.uid ?: return
    val vm: AddExpenseViewModel = viewModel(
        factory = VMFactory { AddExpenseViewModel(container.tripRepository, container.expenseRepository, tripId, expenseId) },
    )
    val trip by vm.trip.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val t = trip
    if (t == null) {
        ScreenScaffold(title = "Expense", onBack = onBack) { CenteredMessage("", loading = true) }
        return
    }

    val members = t.members
    val memberIds = remember(t) { members.mapNotNull { it.memberId } }

    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var amount by remember(existing) { mutableStateOf(existing?.originalAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var currency by remember(existing) { mutableStateOf(existing?.originalCurrency ?: t.destinationCurrency) }
    var category by remember(existing) { mutableStateOf(existing?.category ?: Categories.DEFAULT_NAMES.first()) }
    var paidBy by remember(existing) { mutableStateOf(existing?.paidBy ?: memberIds.firstOrNull().orEmpty()) }
    var splitMode by remember(existing) { mutableStateOf(existing?.splitMode ?: "equal") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Per-member inputs for percentage / exact modes.
    val pct = remember(t) { mutableStateMapOf<String, String>() }
    val exact = remember(t) { mutableStateMapOf<String, String>() }
    LaunchedEffect(existing) {
        existing?.splits?.forEach { s ->
            pct[s.userId] = s.percentage.toString()
            exact[s.userId] = s.amountInDestinationCurrency.toString()
        }
    }

    val allCategories = remember(t) { Categories.DEFAULT_NAMES + t.customCategories.map { it.name } }

    ScreenScaffold(title = if (expenseId == null) "Add expense" else "Edit expense", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            DField(description, { description = it }, "Description")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DField(amount, { amount = it }, "Amount", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                Box(Modifier.width(140.dp)) { CurrencyField(currency, { currency = it }, "Currency") }
            }
            Spacer(Modifier.height(12.dp))
            LabeledDropdown("Category", category, allCategories, onSelect = { category = it }) {
                "${Categories.emojiFor(it)}  $it"
            }
            Spacer(Modifier.height(12.dp))
            LabeledDropdown("Paid by", paidBy, memberIds, onSelect = { paidBy = it }) { id ->
                members.firstOrNull { it.memberId == id }?.displayName ?: id
            }
            Spacer(Modifier.height(16.dp))

            Text("Split", color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("equal" to "Equal", "percentage" to "Percent", "exact" to "Exact").forEach { (mode, label) ->
                    FilterChip(selected = splitMode == mode, onClick = { splitMode = mode }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))

            if (splitMode != "equal") {
                SurfaceCard {
                    members.forEach { m ->
                        val id = m.memberId ?: return@forEach
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.displayName, modifier = Modifier.weight(1f))
                            if (splitMode == "percentage") {
                                DField(pct[id] ?: "", { pct[id] = it }, "%", keyboardType = KeyboardType.Decimal, modifier = Modifier.width(110.dp))
                            } else {
                                DField(exact[id] ?: "", { exact[id] = it }, currency, keyboardType = KeyboardType.Decimal, modifier = Modifier.width(130.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (expenseId == null) "Add expense" else "Save changes",
                enabled = description.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && paidBy.isNotBlank(),
                loading = saving,
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@PrimaryButton
                    val splits: List<SplitInput> = when (splitMode) {
                        "percentage" -> members.mapNotNull { m ->
                            m.memberId?.let { SplitInput(userId = it, percentage = pct[it]?.toDoubleOrNull() ?: 0.0) }
                        }
                        "exact" -> members.mapNotNull { m ->
                            m.memberId?.let { SplitInput(userId = it, exactAmount = exact[it]?.toDoubleOrNull() ?: 0.0, exactCurrency = currency) }
                        }
                        else -> emptyList()
                    }
                    val draft = ExpenseDraft(
                        description = description,
                        category = category,
                        originalAmount = amt,
                        originalCurrency = currency,
                        paidBy = paidBy,
                        splitMode = splitMode,
                        splits = splits,
                    )
                    scope.launch {
                        saving = true; error = null
                        try {
                            vm.submit(draft, currentUid)
                            onBack()
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Could not save expense"
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Danger) }
        }
    }
}

@Composable
private fun <T> LabeledDropdown(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    display: (T) -> String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldCard(label = label, value = display(selected), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(display(opt)) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}
