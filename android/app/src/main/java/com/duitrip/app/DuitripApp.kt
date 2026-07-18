package com.duitrip.app

import android.app.Application
import com.duitrip.app.di.AppContainer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class DuitripApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Offline-first: keep Firestore's local cache so the app works without a
        // connection and reads are instant. Firebase itself auto-initialises from
        // google-services.json via the google-services Gradle plugin.
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        container = AppContainer()
    }
}
