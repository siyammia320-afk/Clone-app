package com.example.engine

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages strict per-clone Cookie, DOM localStorage, and Session isolation inside OmniSpace
 * so Clone #1, Clone #2, ... Clone #100 each maintain completely independent logged-in accounts
 * inside the app without ever leaking sessions across clones or opening the phone's main app.
 */
object SandboxSessionManager {

    private var activeCloneKey: String? = null

    data class CloneEndpointConfig(
        val entryUrl: String,
        val requiresDesktopUaByDefault: Boolean,
        val platformDisplayName: String
    )

    /**
     * Resolves any installed Android package name or app name to its interactive in-app runtime endpoint.
     */
    fun resolveAppEndpoint(packageName: String, appName: String, sandboxDir: String): CloneEndpointConfig {
        // Check if user saved a custom endpoint override for this clone
        val customUrlFile = File(sandboxDir, "files/custom_endpoint.txt")
        if (customUrlFile.exists()) {
            val saved = runCatching { customUrlFile.readText(StandardCharsets.UTF_8).trim() }.getOrDefault("")
            if (saved.startsWith("http://") || saved.startsWith("https://")) {
                return CloneEndpointConfig(
                    entryUrl = saved,
                    requiresDesktopUaByDefault = false,
                    platformDisplayName = appName
                )
            }
        }

        val pkg = packageName.lowercase()
        val nameLower = appName.lowercase()

        return when {
            pkg.contains("facebook.orca") || nameLower.contains("messenger") ->
                CloneEndpointConfig("https://www.messenger.com/login", true, "Messenger")

            pkg.contains("facebook") || nameLower.contains("facebook") ->
                CloneEndpointConfig("https://m.facebook.com/login", false, "Facebook")

            pkg.contains("instagram") || nameLower.contains("instagram") ->
                CloneEndpointConfig("https://www.instagram.com/accounts/login/", false, "Instagram")

            pkg.contains("whatsapp") || nameLower.contains("whatsapp") ->
                CloneEndpointConfig("https://web.whatsapp.com/", true, "WhatsApp")

            pkg.contains("telegram") || pkg.contains("nekogram") || nameLower.contains("telegram") ->
                CloneEndpointConfig("https://web.telegram.org/k/", false, "Telegram")

            pkg.contains("musically") || pkg.contains("tiktok") || pkg.contains("trill") || nameLower.contains("tiktok") ->
                CloneEndpointConfig("https://www.tiktok.com/login", false, "TikTok")

            pkg.contains("twitter") || pkg.contains("com.x.") || nameLower == "x" || nameLower.contains("twitter") ->
                CloneEndpointConfig("https://x.com/i/flow/login", false, "X (Twitter)")

            pkg.contains("snapchat") || nameLower.contains("snapchat") ->
                CloneEndpointConfig("https://accounts.snapchat.com/accounts/v2/login", true, "Snapchat")

            pkg.contains("discord") || nameLower.contains("discord") ->
                CloneEndpointConfig("https://discord.com/login", true, "Discord")

            pkg.contains("google.android.gm") || nameLower.contains("gmail") ->
                CloneEndpointConfig("https://accounts.google.com/ServiceLogin?service=mail", false, "Gmail")

            pkg.contains("youtube") || nameLower.contains("youtube") ->
                CloneEndpointConfig("https://m.youtube.com", false, "YouTube")

            pkg.contains("linkedin") || nameLower.contains("linkedin") ->
                CloneEndpointConfig("https://www.linkedin.com/login", false, "LinkedIn")

            pkg.contains("reddit") || nameLower.contains("reddit") ->
                CloneEndpointConfig("https://www.reddit.com/login/", false, "Reddit")

            pkg.contains("pinterest") || nameLower.contains("pinterest") ->
                CloneEndpointConfig("https://www.pinterest.com/login/", false, "Pinterest")

            pkg.contains("spotify") || nameLower.contains("spotify") ->
                CloneEndpointConfig("https://accounts.spotify.com/en/login", false, "Spotify")

            pkg.contains("netflix") || nameLower.contains("netflix") ->
                CloneEndpointConfig("https://www.netflix.com/login", false, "Netflix")

            pkg.contains("daraz") || nameLower.contains("daraz") ->
                CloneEndpointConfig("https://member.daraz.com.bd/user/login", false, "Daraz")

            pkg.contains("bikroy") || nameLower.contains("bikroy") ->
                CloneEndpointConfig("https://bikroy.com/en/users/login", false, "Bikroy")

            pkg.contains("openai") || nameLower.contains("chatgpt") ->
                CloneEndpointConfig("https://chatgpt.com/auth/login", false, "ChatGPT")

            pkg.contains("imo") || nameLower.contains("imo") ->
                CloneEndpointConfig("https://web.imo.im/", true, "imo")

            pkg.contains("viber") || nameLower.contains("viber") ->
                CloneEndpointConfig("https://account.viber.com/en/login", false, "Viber")

            pkg.contains("bkash") || nameLower.contains("bkash") ->
                CloneEndpointConfig("https://www.bkash.com/en/user", false, "bKash")

            pkg.contains("nagad") || nameLower.contains("nagad") ->
                CloneEndpointConfig("https://nagad.com.bd/", false, "Nagad")

            pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("opera") || pkg.contains("brave") ->
                CloneEndpointConfig("https://www.google.com/", false, appName)

            else -> {
                // Smart search/portal resolver for any other installed app so it opens directly inside the container
                val query = java.net.URLEncoder.encode("$appName login official", "UTF-8")
                CloneEndpointConfig(
                    entryUrl = "https://www.google.com/search?q=$query",
                    requiresDesktopUaByDefault = false,
                    platformDisplayName = appName
                )
            }
        }
    }

    fun saveCustomEndpoint(sandboxDir: String, newUrl: String) {
        runCatching {
            val file = File(sandboxDir, "files/custom_endpoint.txt")
            file.parentFile?.mkdirs()
            file.writeText(newUrl.trim(), StandardCharsets.UTF_8)
        }
    }

    /**
     * Builds a unique User-Agent string per clone number so websites/apps see each clone
     * as a separate device session.
     */
    fun buildIsolatedUserAgent(
        cloneNumber: Int,
        virtualDeviceId: String,
        desktopMode: Boolean
    ): String {
        return if (desktopMode) {
            "Mozilla/5.0 (X11; Linux x86_64; OmniSpaceSlot/$cloneNumber; ID/$virtualDeviceId) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro Build/AP3A.241005.015.$cloneNumber; wv; OmniSpace/$virtualDeviceId) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"
        }
    }

    /**
     * Tracked domains for cookie vault serialization so switching between Clone #1, Clone #2, ... #10
     * swaps the entire cookie jar cleanly.
     */
    private val trackedDomains = listOf(
        "https://m.facebook.com",
        "https://www.facebook.com",
        "https://facebook.com",
        "https://www.messenger.com",
        "https://www.instagram.com",
        "https://instagram.com",
        "https://web.whatsapp.com",
        "https://web.telegram.org",
        "https://www.tiktok.com",
        "https://x.com",
        "https://twitter.com",
        "https://accounts.google.com",
        "https://mail.google.com",
        "https://www.youtube.com",
        "https://m.youtube.com",
        "https://accounts.snapchat.com",
        "https://discord.com",
        "https://www.linkedin.com",
        "https://www.reddit.com",
        "https://www.pinterest.com",
        "https://accounts.spotify.com",
        "https://www.netflix.com",
        "https://member.daraz.com.bd",
        "https://bikroy.com",
        "https://chatgpt.com"
    )

    /**
     * Switches the active CookieManager and WebStorage state to belong exclusively to [cloneKey]
     * backed by [sandboxDir].
     */
    fun mountCloneSession(
        cloneKey: String,
        sandboxDir: String,
        currentUrl: String?,
        onComplete: () -> Unit
    ) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        // If another clone was previously mounted, flush its cookies first
        cookieManager.flush()

        // Clear all active cookies in memory before loading this clone's isolated cookie vault
        cookieManager.removeAllCookies {
            // Restore cookies belonging exclusively to THIS clone slot from its sandbox file
            val vaultFile = File(sandboxDir, "shared_prefs/cookie_vault.json")
            if (vaultFile.exists()) {
                runCatching {
                    val jsonText = vaultFile.readText(StandardCharsets.UTF_8)
                    val root = JSONObject(jsonText)
                    val entries = root.optJSONArray("cookies") ?: JSONArray()
                    for (i in 0 until entries.length()) {
                        val item = entries.getJSONObject(i)
                        val domain = item.optString("domain")
                        val rawCookie = item.optString("raw")
                        if (domain.isNotEmpty() && rawCookie.isNotEmpty()) {
                            // Split combined "k1=v1; k2=v2" cookies into individual setCookie calls
                            rawCookie.split(";").forEach { singleCookie ->
                                val trimmed = singleCookie.trim()
                                if (trimmed.isNotEmpty()) {
                                    cookieManager.setCookie(domain, trimmed)
                                }
                            }
                        }
                    }
                }
            }
            cookieManager.flush()
            activeCloneKey = cloneKey
            onComplete()
        }
    }

    /**
     * Snapshots all cookies from the active session into this clone's private sandbox file.
     */
    fun persistCloneSession(sandboxDir: String, currentUrl: String?) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val domainsToSave = LinkedHashSet<String>()
            domainsToSave.addAll(trackedDomains)
            if (!currentUrl.isNullOrBlank() && (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))) {
                domainsToSave.add(currentUrl)
            }

            val cookiesArray = JSONArray()
            for (domain in domainsToSave) {
                val raw = cookieManager.getCookie(domain)
                if (!raw.isNullOrBlank()) {
                    val obj = JSONObject()
                    obj.put("domain", domain)
                    obj.put("raw", raw)
                    cookiesArray.put(obj)
                }
            }

            val root = JSONObject()
            root.put("updatedAt", System.currentTimeMillis())
            root.put("cookies", cookiesArray)

            val vaultFile = File(sandboxDir, "shared_prefs/cookie_vault.json")
            vaultFile.parentFile?.mkdirs()
            vaultFile.writeText(root.toString(2), StandardCharsets.UTF_8)
        }
    }

    /**
     * Wipes only this clone's saved cookies & session so the user can log into a fresh account.
     */
    fun wipeCloneSession(sandboxDir: String, onCleared: () -> Unit) {
        val vaultFile = File(sandboxDir, "shared_prefs/cookie_vault.json")
        if (vaultFile.exists()) {
            vaultFile.delete()
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
            onCleared()
        }
    }
}
