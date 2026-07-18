package com.duitrip.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duitrip.app.di.AppContainer

/** Access the app's dependency container from any composable. */
val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

/** Minimal ViewModel factory that defers to a constructor lambda. */
class VMFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

/** Navigation routes — mirror the web app's react-router paths. */
object Routes {
    const val LANDING = "landing"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val PROFILE = "profile"
    const val NEW_TRIP = "trips/new"

    fun tripDetail(tripId: String) = "trips/$tripId"
    fun analytics(tripId: String) = "trips/$tripId/analytics"
    fun newExpense(tripId: String) = "trips/$tripId/expenses/new"
    fun editExpense(tripId: String, expenseId: String) = "trips/$tripId/expenses/$expenseId/edit"
    fun settlement(tripId: String) = "trips/$tripId/settlement"
    fun members(tripId: String) = "trips/$tripId/members"
    fun invite(tripId: String) = "invite/$tripId"

    const val TRIP_DETAIL = "trips/{tripId}"
    const val ANALYTICS = "trips/{tripId}/analytics"
    const val NEW_EXPENSE = "trips/{tripId}/expenses/new"
    const val EDIT_EXPENSE = "trips/{tripId}/expenses/{expenseId}/edit"
    const val SETTLEMENT = "trips/{tripId}/settlement"
    const val MEMBERS = "trips/{tripId}/members"
    const val INVITE = "invite/{tripId}"
}
