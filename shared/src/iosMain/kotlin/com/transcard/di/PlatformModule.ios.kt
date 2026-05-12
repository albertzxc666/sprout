package com.transcard.di

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import com.transcard.data.db.DatabaseDriverFactory
import com.transcard.data.preferences.Preferences
import org.koin.dsl.module

@OptIn(ExperimentalSettingsImplementation::class)
actual fun platformModule() = module {
    single { DatabaseDriverFactory() }
    single { Preferences() }

    single<Settings> { KeychainSettings(service = "com.transcard.sprout") }
}
