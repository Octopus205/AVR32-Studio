package com.example.octoavr.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.octoavr.model.EditorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.octoDataStore: DataStore<Preferences> by preferencesDataStore(name = "octoavr_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
        val RECENT_PROJECT_IDS = stringPreferencesKey("recent_project_ids")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_TAB_WIDTH = intPreferencesKey("editor_tab_width")
        val AUTO_CLOSE_BRACKETS = booleanPreferencesKey("auto_close_brackets")
        val AUTO_INDENT = booleanPreferencesKey("auto_indent")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val UART_BAUD_RATE = intPreferencesKey("uart_baud_rate")
    }

    val editorSettingsFlow: Flow<EditorSettings> = context.octoDataStore.data.map { prefs ->
        EditorSettings(
            fontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 13,
            tabWidth = prefs[Keys.EDITOR_TAB_WIDTH] ?: 4,
            autoCloseBrackets = prefs[Keys.AUTO_CLOSE_BRACKETS] ?: true,
            autoIndent = prefs[Keys.AUTO_INDENT] ?: true,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true
        )
    }

    val uartBaudRateFlow: Flow<Int> = context.octoDataStore.data.map { prefs ->
        prefs[Keys.UART_BAUD_RATE] ?: 9600
    }

    suspend fun getActiveProjectId(): String? =
        context.octoDataStore.data.first()[Keys.ACTIVE_PROJECT_ID]

    suspend fun setActiveProjectId(projectId: String) {
        context.octoDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROJECT_ID] = projectId
            val current = prefs[Keys.RECENT_PROJECT_IDS]
                ?.split(",")
                ?.filter { it.isNotBlank() && it != projectId }
                ?: emptyList()
            prefs[Keys.RECENT_PROJECT_IDS] = (listOf(projectId) + current).take(12).joinToString(",")
        }
    }

    suspend fun getRecentProjectIds(): List<String> {
        val raw = context.octoDataStore.data.first()[Keys.RECENT_PROJECT_IDS] ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    suspend fun removeRecentProjectId(projectId: String) {
        context.octoDataStore.edit { prefs ->
            val updated = (prefs[Keys.RECENT_PROJECT_IDS] ?: "")
                .split(",")
                .filter { it.isNotBlank() && it != projectId }
            prefs[Keys.RECENT_PROJECT_IDS] = updated.joinToString(",")
            if (prefs[Keys.ACTIVE_PROJECT_ID] == projectId) {
                prefs.remove(Keys.ACTIVE_PROJECT_ID)
            }
        }
    }

    suspend fun updateEditorSettings(settings: EditorSettings) {
        context.octoDataStore.edit { prefs ->
            prefs[Keys.EDITOR_FONT_SIZE] = settings.fontSizeSp.coerceIn(10, 22)
            prefs[Keys.EDITOR_TAB_WIDTH] = settings.tabWidth.coerceIn(2, 8)
            prefs[Keys.AUTO_CLOSE_BRACKETS] = settings.autoCloseBrackets
            prefs[Keys.AUTO_INDENT] = settings.autoIndent
            prefs[Keys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
        }
    }

    suspend fun setUartBaudRate(baud: Int) {
        context.octoDataStore.edit { prefs ->
            prefs[Keys.UART_BAUD_RATE] = baud
        }
    }
}
