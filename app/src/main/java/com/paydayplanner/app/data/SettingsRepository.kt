package com.paydayplanner.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

data class Settings(
    /** Day of month of the first payday (1–31). */
    val payday1: Int = 15,
    /** Day of month of the second payday (1–31). */
    val payday2: Int = 30,
    /** Expected income on payday 1, in cents. */
    val income1: Long = 0,
    /** Expected income on payday 2, in cents. */
    val income2: Long = 0,
    /** Default spending cap per period (excluding bills), in cents. 0 = no cap. */
    val capCents: Long = 0,
    val currency: String = defaultCurrencySymbol(),
    val remindersEnabled: Boolean = true,
    val remindDaysBefore: Int = 2,
)

private fun defaultCurrencySymbol(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).symbol }.getOrDefault("$")

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val payday1 = intPreferencesKey("payday1")
        val payday2 = intPreferencesKey("payday2")
        val income1 = longPreferencesKey("income1")
        val income2 = longPreferencesKey("income2")
        val cap = longPreferencesKey("cap")
        val currency = stringPreferencesKey("currency")
        val reminders = booleanPreferencesKey("reminders")
        val remindDays = intPreferencesKey("remind_days")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun save(s: Settings) {
        context.dataStore.edit { it.write(s) }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            payday1 = this[Keys.payday1] ?: d.payday1,
            payday2 = this[Keys.payday2] ?: d.payday2,
            income1 = this[Keys.income1] ?: d.income1,
            income2 = this[Keys.income2] ?: d.income2,
            capCents = this[Keys.cap] ?: d.capCents,
            currency = this[Keys.currency] ?: d.currency,
            remindersEnabled = this[Keys.reminders] ?: d.remindersEnabled,
            remindDaysBefore = this[Keys.remindDays] ?: d.remindDaysBefore,
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.payday1] = s.payday1
        this[Keys.payday2] = s.payday2
        this[Keys.income1] = s.income1
        this[Keys.income2] = s.income2
        this[Keys.cap] = s.capCents
        this[Keys.currency] = s.currency
        this[Keys.reminders] = s.remindersEnabled
        this[Keys.remindDays] = s.remindDaysBefore
    }
}
