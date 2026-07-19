package com.duitrip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.atan2
import kotlin.math.sqrt

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
            if (analytics.group.byCategory.isNotEmpty()) item {
                PieChart(analytics.group.byCategory.map { PieSlice("${it.emoji}  ${it.category}", it.amount, it.percentage) }, cur)
            }

            // Spend by day (parity with the PWA's by-day chart)
            if (analytics.group.byDay.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); Text("By day", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
                item {
                    val maxAmount = analytics.group.byDay.maxOf { it.amount }.coerceAtLeast(0.01)
                    SurfaceCard {
                        analytics.group.byDay.forEach { day ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day.date, color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    "${Format.currency(day.amount, cur)} · ${day.expenseCount} exp",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(BgBorder)) {
                                Box(
                                    Modifier.fillMaxWidth((day.amount / maxAmount).toFloat().coerceIn(0f, 1f))
                                        .height(8.dp).clip(RoundedCornerShape(4.dp)).background(Teal),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)); Text("By member", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            if (analytics.group.byMember.isNotEmpty()) item {
                PieChart(analytics.group.byMember.map { PieSlice(it.displayName + if (it.isGhost) " 👻" else "", it.totalPaid, it.percentage) }, cur)
            }
        }
    }
}

private data class PieSlice(val label: String, val amount: Double, val percentage: Double)

/**
 * Donut chart on top (tap a slice to see its amount/percentage), plain legend below —
 * stacking them (instead of side-by-side) avoids the chart and labels overlapping
 * regardless of how long a category/member name is.
 */
@Composable
private fun PieChart(slices: List<PieSlice>, currency: String) {
    val colors = listOf(Color(0xFF4DC3EA), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899))
    var selected by remember(slices) { mutableStateOf<Int?>(null) }

    SurfaceCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val strokeWidth = 32.dp
            Canvas(
                Modifier
                    .size(200.dp)
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val distance = sqrt(dx * dx + dy * dy)
                            val outerRadius = size.width / 2f
                            val strokePx = strokeWidth.toPx()
                            if (distance < outerRadius - strokePx || distance > outerRadius) {
                                selected = null
                                return@detectTapGestures
                            }
                            val rawDeg = Math.toDegrees(atan2(dy, dx).toDouble())
                            val angle = (rawDeg + 90.0 + 360.0) % 360.0
                            var cumulative = 0.0
                            var hit: Int? = null
                            for ((index, slice) in slices.withIndex()) {
                                val sweep = slice.percentage * 3.6
                                if (angle >= cumulative && angle < cumulative + sweep) {
                                    hit = index
                                    break
                                }
                                cumulative += sweep
                            }
                            selected = hit
                        }
                    },
            ) {
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = (slice.percentage * 3.6).toFloat()
                    if (sweep > 0f) {
                        val width = if (selected == index) strokeWidth.toPx() + 8.dp.toPx() else strokeWidth.toPx()
                        drawArc(colors[index % colors.size], start, sweep, useCenter = false, style = Stroke(width = width))
                    }
                    start += sweep
                }
            }
            val sel = selected?.let { slices.getOrNull(it) }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (sel != null) {
                    Text(sel.label, color = TextPrimary, fontSize = 13.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(Format.currency(sel.amount, currency), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${sel.percentage}%", color = TextSecondary, fontSize = 13.sp)
                } else {
                    Text("Tap a slice", color = TextSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEachIndexed { index, slice ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (selected == index) null else index },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(colors[index % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(slice.label, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Format.currency(slice.amount, currency)} · ${slice.percentage}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
