package com.transcard.data.sync

import com.transcard.db.TransCardDatabase

/**
 * Маленький helper, который репозитории дёргают после insert/update/delete.
 * Просто помечает SyncState.pendingPush = 1. Дальше SyncManager сам решит,
 * когда пушить (debounce + auth check).
 */
class SyncTrigger(private val db: TransCardDatabase) {
    fun markDirty() {
        db.syncStateQueries.markDirty()
    }
}
