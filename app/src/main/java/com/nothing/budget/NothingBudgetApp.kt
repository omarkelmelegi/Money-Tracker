package com.nothing.budget

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class NothingBudgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme preference. Default is FOLLOW_SYSTEM.
        AppCompatDelegate.setDefaultNightMode(Storage(this).themeMode.toAppCompatMode())
    }
}
