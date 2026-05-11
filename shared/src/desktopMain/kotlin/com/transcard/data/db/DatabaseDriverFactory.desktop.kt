package com.transcard.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.transcard.db.TransCardDatabase
import java.io.File
import java.util.Properties

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".transcard").apply { mkdirs() }
        val dbFile = File(dbDir, "transcard.db")
        val isNew = !dbFile.exists()

        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties().apply { put("foreign_keys", "true") }
        )
        if (isNew) {
            TransCardDatabase.Schema.create(driver)
        }
        return driver
    }
}
