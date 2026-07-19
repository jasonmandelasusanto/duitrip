package com.duitrip.app.di

import android.content.Context
import com.duitrip.app.data.AuthRepository
import com.duitrip.app.data.BackupRepository
import com.duitrip.app.data.ExpenseRepository
import com.duitrip.app.data.SettlementRepository
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.UpdateRepository
import com.duitrip.app.data.UserRepository

/** Tiny manual dependency container — one instance lives on the Application. */
class AppContainer(appContext: Context) {
    val authRepository by lazy { AuthRepository() }
    val userRepository by lazy { UserRepository() }
    val tripRepository by lazy { TripRepository() }
    val expenseRepository by lazy { ExpenseRepository() }
    val settlementRepository by lazy { SettlementRepository() }
    val backupRepository by lazy { BackupRepository(tripRepository, expenseRepository, settlementRepository) }
    val updateRepository by lazy { UpdateRepository(appContext) }
}
