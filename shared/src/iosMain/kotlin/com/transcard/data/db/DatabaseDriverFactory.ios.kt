package com.transcard.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.transcard.db.TransCardDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver {
        val driver = NativeSqliteDriver(TransCardDatabase.Schema, "transcard.db")
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}
