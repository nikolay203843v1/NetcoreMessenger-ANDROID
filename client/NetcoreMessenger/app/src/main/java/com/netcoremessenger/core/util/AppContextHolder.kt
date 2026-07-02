package com.netcoremessenger.core.util

import android.content.Context

/**
 * Хранит applicationContext, чтобы к нему мог обратиться [ServerConfig]
 * (обычный object, без DI) ещё до создания Hilt-графа.
 * Заполняется в NetcoreMessengerApp.attachBaseContext().
 */
object AppContextHolder {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            // В момент Application.attachBaseContext() у самого Application
            // ещё не выставлен mBase, поэтому context.applicationContext
            // может вернуть null. Берём переданный base как есть — этого
            // достаточно для SharedPreferences и прочих нужд ServerConfig.
            appContext = context.applicationContext ?: context
        }
    }
}
