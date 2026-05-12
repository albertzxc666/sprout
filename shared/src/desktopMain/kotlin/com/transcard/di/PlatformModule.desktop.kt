package com.transcard.di

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.JvmPreferencesSettings
import com.russhwolf.settings.Settings
import com.transcard.data.db.DatabaseDriverFactory
import com.transcard.data.preferences.Preferences
import org.koin.dsl.module
import java.util.prefs.Preferences as JvmPrefs

@OptIn(ExperimentalSettingsImplementation::class)
actual fun platformModule() = module {
    single { DatabaseDriverFactory() }
    single { Preferences() }

    // NOTE: Java Preferences API хранит данные в user-scoped storage
    // (на Windows — registry HKCU, на Linux/Mac — ~/.java/.userPrefs/).
    // Данные НЕ зашифрованы — это known limitation для Desktop в MVP.
    // На iOS и Android — настоящее secure storage (Keychain / EncryptedSharedPreferences).
    single<Settings> {
        JvmPreferencesSettings(JvmPrefs.userRoot().node("com.transcard.sprout"))
    }
}
