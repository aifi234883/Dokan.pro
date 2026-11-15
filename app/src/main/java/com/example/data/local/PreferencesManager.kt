package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dukanpro_prefs")

data class AppPreferences(
  val isUrdu: Boolean = false,
  val isDarkTheme: Boolean = false,
  val selectedShopId: String = "",
)

class PreferencesManager(private val context: Context) {
  private object Keys {
    val IS_URDU = booleanPreferencesKey("is_urdu")
    val IS_DARK = booleanPreferencesKey("is_dark_theme")
    val SELECTED_SHOP_ID = stringPreferencesKey("selected_shop_id")
  }

  val preferencesFlow: Flow<AppPreferences> =
    context.dataStore.data.map { prefs ->
      AppPreferences(
        isUrdu = prefs[Keys.IS_URDU] ?: false,
        isDarkTheme = prefs[Keys.IS_DARK] ?: false,
        selectedShopId = prefs[Keys.SELECTED_SHOP_ID] ?: "",
      )
    }

  suspend fun setUrdu(enabled: Boolean) {
    context.dataStore.edit { it[Keys.IS_URDU] = enabled }
  }

  suspend fun setDarkTheme(enabled: Boolean) {
    context.dataStore.edit { it[Keys.IS_DARK] = enabled }
  }

  suspend fun setSelectedShopId(shopId: String) {
    context.dataStore.edit { it[Keys.SELECTED_SHOP_ID] = shopId }
  }
}
