package com.transcard.di

import com.transcard.data.db.DatabaseDriverFactory
import com.transcard.data.preferences.Preferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { Preferences(androidContext()) }
}
