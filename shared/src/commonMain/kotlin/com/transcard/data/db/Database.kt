package com.transcard.data.db

import com.transcard.db.TransCardDatabase

fun createDatabase(factory: DatabaseDriverFactory): TransCardDatabase =
    TransCardDatabase(factory.create())
