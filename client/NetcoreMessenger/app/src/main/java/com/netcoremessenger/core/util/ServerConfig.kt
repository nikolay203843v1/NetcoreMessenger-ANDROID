package com.netcoremessenger.core.util

import android.content.Context
import android.util.Log
import com.netcoremessenger.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Автоматически подтягивает актуальный адрес сервера из Telegram-канала
 * (туда бэкенд шлёт новый домен при каждом рестарте туннеля).
 *
 * Никакого ручного ввода пользователем не требуется: приложение читает
 * канал через Bot API ещё до старта Hilt-графа (см. NetcoreMessengerApp),
 * поэтому Retrofit/OkHttp/WebSocket всегда создаются уже с актуальным адресом.
 *
 * Если Telegram недоступен (нет сети, бот не настроен и т.п.) — используется
 * последний успешно закэшированный адрес, а если его тоже нет — BASE_URL/WS_URL
 * из BuildConfig (фолбэк на случай самого первого запуска).
 */
object ServerConfig {
    private const val TAG = "ServerConfig"
    private const val PREFS = "server_config"
    private const val KEY_DOMAIN = "domain"

    private fun prefs() = AppContextHolder.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getDomain(): String? = prefs().getString(KEY_DOMAIN, null)

    private fun saveDomain(domain: String) {
        prefs().edit().putString(KEY_DOMAIN, domain).apply()
    }

    /**
     * Блокирующий сетевой вызов. Вызывается один раз, синхронно,
     * в NetcoreMessengerApp.attachBaseContext() — то есть ДО того,
     * как Hilt создаст Retrofit/OkHttp (которые читают Constants.BASE_URL).
     * Таймаут короткий, чтобы не задерживать старт приложения при плохой сети.
     */
    fun refreshFromTelegramBlocking() {
        val token = BuildConfig.TELEGRAM_BOT_TOKEN
        val chatId = BuildConfig.TELEGRAM_CHAT_ID
        if (token.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "TELEGRAM_BOT_TOKEN/CHAT_ID не заданы — использую закэшированный/дефолтный адрес")
            return
        }
        // Если кэша нет — это первый запуск, и получить домен КРАЙНЕ важно (иначе
        // Retrofit соберётся с example.invalid и всё сломается). Ждём дольше и с ретраем.
        // Если кэш есть — не задерживаем старт: одна короткая попытка, фон дописывает кэш.
        val hasCache = !getDomain().isNullOrBlank()
        val attempts = if (hasCache) 1 else 3
        val perAttemptTimeoutMs = if (hasCache) 4000 else 8000
        val gotDomain = java.util.concurrent.atomic.AtomicBoolean(false)

        val worker = Thread {
            repeat(attempts) { attempt ->
                if (gotDomain.get()) return@Thread
                var connection: HttpURLConnection? = null
                try {
                    // getChat возвращает объект чата, включая pinned_message — стабильно:
                    // не зависит от 24-часового окна апдейтов, offset getUpdates и webhook’а.
                    val encodedChat = URLEncoder.encode(chatId, "UTF-8")
                    val url = URL("https://api.telegram.org/bot$token/getChat?chat_id=$encodedChat")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = perAttemptTimeoutMs
                        readTimeout = perAttemptTimeoutMs
                        requestMethod = "GET"
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) {
                        Log.w(TAG, "Telegram getChat HTTP $code (попытка ${attempt + 1}/$attempts): $body")
                    } else {
                        val domain = parsePinnedDomain(body)
                        if (domain != null) {
                            Log.i(TAG, "Получен адрес сервера из pinned Telegram: $domain")
                            saveDomain(domain)
                            gotDomain.set(true)
                            return@Thread
                        } else {
                            Log.w(TAG, "В pinned_message нет валидного домена (попытка ${attempt + 1}/$attempts): $body")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ошибка getChat (попытка ${attempt + 1}/$attempts): ${e.message}")
                } finally {
                    connection?.disconnect()
                }
                // Небольшая пауза между попытками, чтобы дождаться DNS/сети.
                if (attempt < attempts - 1) Thread.sleep(1000)
            }
        }
        worker.isDaemon = true
        worker.start()
        // Общий бюджет ожидания: количество попыток × таймаут + паузы + запас.
        val totalWaitMs = (attempts * perAttemptTimeoutMs + (attempts - 1) * 1000 + 2000).toLong()
        worker.join(totalWaitMs)
        if (!gotDomain.get() && !hasCache) {
            Log.e(TAG, "Не удалось получить домен из Telegram на первом запуске — Retrofit соберётся с fallback URL")
        }
    }

    private fun parsePinnedDomain(json: String): String? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("ok", false)) return null
            val result = root.optJSONObject("result") ?: return null
            val pinned = result.optJSONObject("pinned_message") ?: return null
            val text = pinned.optString("text", "")
            extractDomain(text)
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка парсинга ответа Telegram: ${e.message}")
            null
        }
    }

    private fun extractDomain(text: String): String? {
        val candidate = text.lines()
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() }
            ?: return null
        val cleaned = candidate
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
        // Простая валидация: похоже на домен (есть точка, нет пробелов/эмодзи)
        if (cleaned.isBlank() || cleaned.contains(" ") || !cleaned.contains(".")) return null
        return cleaned
    }

    /**
     * ВАЖНО: возвращается БЕЗ trailing '/'. По всему приложению код делает
     * "${Constants.BASE_URL}/api/v1/...", и если бы здесь был слэш, получался бы
     * двойной '//', на который FastAPI отвечает 307-редиректом, а Coil/OkHttp
     * при таком редиректе теряет часть заголовков — в итоге аватарки/фото серые.
     * Слэш для Retrofit добавляется отдельно (см. [baseUrlForRetrofit]).
     */
    fun baseUrl(): String {
        val domain = getDomain()
        val raw = if (!domain.isNullOrBlank()) "https://$domain" else BuildConfig.BASE_URL
        return raw.trimEnd('/')
    }

    /** Только для Retrofit.Builder().baseUrl() — он требует обязательный '/' в конце. */
    fun baseUrlForRetrofit(): String = baseUrl() + "/"

    fun wsUrl(): String {
        val domain = getDomain()
        val raw = if (!domain.isNullOrBlank()) "wss://$domain" else BuildConfig.WS_URL
        return raw.trimEnd('/')
    }
}
