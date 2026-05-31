package com.moleculesoft.dcs

import android.app.Application
import com.google.firebase.FirebaseApp

class DcsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
