package com.lianyu.ai.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 为 feature:chat 与 feature:notification 提供 chat_detail_settings DataStore 的单一实例。
 *
 * `preferencesDataStore` 委托按属性对象缓存实例；在不同模块中各自定义同名扩展属性会创建多个
 * DataStore 并触发 "multiple DataStores active for the same file" 异常。因此集中在此单例中定义。
 */
object ChatDetailSettingsDataStoreProvider {
    private const val NAME = "chat_detail_settings"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = NAME)

    fun get(context: Context): DataStore<Preferences> = context.applicationContext.dataStore
}
