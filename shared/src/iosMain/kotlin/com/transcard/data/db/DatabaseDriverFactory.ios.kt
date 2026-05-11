package com.transcard.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.transcard.db.TransCardDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(
        schema = TransCardDatabase.Schema,
        name = "transcard.db"
    )
}
