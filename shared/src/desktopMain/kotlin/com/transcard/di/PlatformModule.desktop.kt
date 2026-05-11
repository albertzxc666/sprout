package com.transcard.di

import com.transcard.data.db.DatabaseDriverFactory
import com.transcard.data.preferences.Preferences
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DatabaseDriverFactory() }
    single { Preferences() }
}
