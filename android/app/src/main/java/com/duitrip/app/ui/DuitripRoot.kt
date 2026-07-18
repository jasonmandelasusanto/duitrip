package com.duitrip.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.duitrip.app.data.model.User
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.screens.AddExpenseScreen
import com.duitrip.app.ui.screens.AnalyticsScreen
import com.duitrip.app.ui.screens.DashboardScreen
import com.duitrip.app.ui.screens.InviteAcceptScreen
import com.duitrip.app.ui.screens.LandingScreen
import com.duitrip.app.ui.screens.MembersScreen
import com.duitrip.app.ui.screens.NewTripScreen
import com.duitrip.app.ui.screens.OnboardingScreen
import com.duitrip.app.ui.screens.ProfileScreen
import com.duitrip.app.ui.screens.SettlementScreen
import com.duitrip.app.ui.screens.TripDetailScreen

/** Current signed-in user (null when signed out / still loading). */
val LocalCurrentUser = compositionLocalOf<User?> { null }

@Composable
fun DuitripRoot() {
    val container = LocalContainer.current
    val vm: RootViewModel = viewModel(
        factory = VMFactory { RootViewModel(container.authRepository, container.userRepository) },
    )
    val gate by vm.state.collectAsStateWithLifecycle()

    when (val g = gate) {
        AuthGate.Loading -> CenteredMessage("", loading = true)
        else -> {
            val currentUser = when (g) {
                is AuthGate.Ready -> g.user
                is AuthGate.NeedsOnboarding -> g.user
                else -> null
            }
            val start = when (g) {
                is AuthGate.SignedOut -> Routes.LANDING
                is AuthGate.NeedsOnboarding -> Routes.ONBOARDING
                else -> Routes.DASHBOARD
            }
            CompositionLocalProvider(LocalCurrentUser provides currentUser) {
                AppNavHost(start = start, onSignOut = vm::signOut)
            }
        }
    }
}

@Composable
private fun AppNavHost(start: String, onSignOut: () -> Unit) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LANDING) { LandingScreen() }
        composable(Routes.ONBOARDING) { OnboardingScreen() }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenTrip = { nav.navigate(Routes.tripDetail(it)) },
                onNewTrip = { nav.navigate(Routes.NEW_TRIP) },
                onProfile = { nav.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { nav.popBackStack() }, onSignOut = onSignOut)
        }
        composable(Routes.NEW_TRIP) {
            NewTripScreen(
                onBack = { nav.popBackStack() },
                onCreated = { tripId ->
                    nav.navigate(Routes.tripDetail(tripId)) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
            )
        }
        composable(Routes.TRIP_DETAIL) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            TripDetailScreen(
                tripId = tripId,
                onBack = { nav.popBackStack() },
                onAddExpense = { nav.navigate(Routes.newExpense(tripId)) },
                onEditExpense = { eid -> nav.navigate(Routes.editExpense(tripId, eid)) },
                onMembers = { nav.navigate(Routes.members(tripId)) },
                onSettlement = { nav.navigate(Routes.settlement(tripId)) },
                onAnalytics = { nav.navigate(Routes.analytics(tripId)) },
            )
        }
        composable(Routes.NEW_EXPENSE) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            AddExpenseScreen(tripId = tripId, expenseId = null, onBack = { nav.popBackStack() })
        }
        composable(Routes.EDIT_EXPENSE) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            val expenseId = entry.arguments?.getString("expenseId")
            AddExpenseScreen(tripId = tripId, expenseId = expenseId, onBack = { nav.popBackStack() })
        }
        composable(Routes.MEMBERS) { entry ->
            MembersScreen(tripId = entry.arguments?.getString("tripId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTLEMENT) { entry ->
            SettlementScreen(tripId = entry.arguments?.getString("tripId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.ANALYTICS) { entry ->
            AnalyticsScreen(tripId = entry.arguments?.getString("tripId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.INVITE,
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://duitrip.app/invite/{tripId}" },
                navDeepLink { uriPattern = "duitrip://invite/{tripId}" },
            ),
        ) { entry ->
            InviteAcceptScreen(
                tripId = entry.arguments?.getString("tripId").orEmpty(),
                onBack = { nav.popBackStack() },
                onAccepted = { tripId ->
                    nav.navigate(Routes.tripDetail(tripId)) { popUpTo(Routes.DASHBOARD) }
                },
            )
        }
    }
}
