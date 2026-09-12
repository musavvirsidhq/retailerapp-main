package com.retailapp.android.session

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Backend auth is a plain session cookie (`retailapp_session`, see internal/handlers/auth.go),
 * not a bearer token - so the app must behave like a browser and keep whatever cookies the
 * server sets, sending them back on every request. This jar persists cookies to SharedPreferences
 * so a logged-in session survives an app restart.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        prefs.getString(KEY_COOKIES, null)?.split(ENTRY_SEPARATOR)?.forEach { entry ->
            if (entry.isBlank()) return@forEach
            val (host, cookieString) = entry.split(HOST_SEPARATOR, limit = 2).let { it[0] to it[1] }
            val dummyUrl = HttpUrl.Builder().scheme("http").host(host).build()
            Cookie.parse(dummyUrl, cookieString)?.let { cookie ->
                store.getOrPut(host) { mutableListOf() }.add(cookie)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name }
            list.add(newCookie)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val hostCookies = store[url.host] ?: return emptyList()
        val expired = hostCookies.filter { it.expiresAt <= now }
        if (expired.isNotEmpty()) {
            hostCookies.removeAll(expired)
            persist()
        }
        return hostCookies.toList()
    }

    /** Wipes every stored cookie. Call this on logout so a stale session can't be reused. */
    fun clear() {
        store.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private fun persist() {
        val serialized = store.entries.joinToString(ENTRY_SEPARATOR) { (host, cookies) ->
            cookies.joinToString(ENTRY_SEPARATOR) { cookie -> "$host$HOST_SEPARATOR$cookie" }
        }
        prefs.edit().putString(KEY_COOKIES, serialized).apply()
    }

    private companion object {
        const val PREFS_NAME = "retailapp_cookies"
        const val KEY_COOKIES = "cookies"
        const val ENTRY_SEPARATOR = "\n"
        const val HOST_SEPARATOR = "\t"
    }
}
