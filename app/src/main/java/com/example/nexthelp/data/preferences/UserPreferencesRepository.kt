package com.example.nexthelp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexthelp_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_SEEN_NOTIFICATIONS = longPreferencesKey("last_seen_notifications")
        val NOTIFY_STATUS_CHANGES = booleanPreferencesKey("notify_status_changes")
        val NOTIFY_COMMENTS = booleanPreferencesKey("notify_comments")
        val NOTIFY_PRIORITY = booleanPreferencesKey("notify_priority")
    }

    val lastSeenNotifications: Flow<Long> = context.dataStore.data
        .map { it[Keys.LAST_SEEN_NOTIFICATIONS] ?: 0L }

    val notifyStatusChanges: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.NOTIFY_STATUS_CHANGES] ?: true }

    val notifyComments: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.NOTIFY_COMMENTS] ?: true }

    val notifyPriority: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.NOTIFY_PRIORITY] ?: true }

    suspend fun setLastSeenNotifications(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SEEN_NOTIFICATIONS] = timestamp }
    }

    suspend fun setNotifyStatusChanges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_STATUS_CHANGES] = enabled }
    }

    suspend fun setNotifyComments(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_COMMENTS] = enabled }
    }

    suspend fun setNotifyPriority(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_PRIORITY] = enabled }
    }
}
