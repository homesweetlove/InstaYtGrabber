package com.homesweetlove.instaytgrabber.feature.instagram

import android.content.Context
import com.chaquo.python.Python
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class InstagramCatalogItem(
    val id: String,
    val kind: String,
    val title: String,
    val targetUrl: String,
    val previewUrl: String,
    val isVideo: Boolean,
    val date: String,
    val count: Int
)

data class InstagramProfileCatalog(
    val username: String,
    val fullName: String,
    val biography: String,
    val isPrivate: Boolean,
    val avatar: InstagramCatalogItem?,
    val stories: List<InstagramCatalogItem> = emptyList(),
    val highlights: List<InstagramCatalogItem> = emptyList(),
    val posts: List<InstagramCatalogItem> = emptyList(),
    val reels: List<InstagramCatalogItem> = emptyList(),
    val errors: Map<String, String> = emptyMap()
)

enum class InstagramCatalogSection(val wireName: String) {
    STORIES("stories"),
    HIGHLIGHTS("highlights"),
    POSTS("posts"),
    REELS("reels")
}

object InstagramProfileCatalogEngine {
    private fun logFailure(code: String, stage: String, result: Result<*>) {
        result.exceptionOrNull()?.let {
            if (stage.contains("Instaloader") && InstagramRateLimit.onFailure(it.message)) {
                DiagnosticLog.record(
                    "IG-RATE-429", "Instagram 요청 제한",
                    "Instaloader에서 429 응답을 받았습니다. 15분 동안 같은 엔진 호출을 중단합니다."
                )
            }
            DiagnosticLog.failure(code, stage, it)
        }
    }

    suspend fun loadBase(
        context: Context,
        profileUrl: String
    ): Result<InstagramProfileCatalog> {
        if (InstagramRateLimit.shouldSkip() && !InstagramSession.isLoggedIn()) {
            return Result.failure(IllegalStateException(
                "Instagram 요청 제한(429)으로 자동 조회를 잠시 중단했습니다. 약 " +
                    InstagramRateLimit.remainingMinutes() +
                    "분 뒤 다시 시도하거나, 로그인 페이지가 열리는지 먼저 확인해 주세요."
            ))
        }
        val skipInstaloader = InstagramRateLimit.shouldSkip()
        val instaloader = if (skipInstaloader) {
            Result.failure(IllegalStateException(
                "Instagram 요청 제한 대기 중: 약 " +
                    InstagramRateLimit.remainingMinutes() + "분 남음"
            ))
        } else {
            withContext(Dispatchers.IO) {
                runCatching { callInstaloaderBase(profileUrl, "") }
            }
        }
        if (instaloader.isSuccess) return instaloader
        if (!skipInstaloader) {
            logFailure("IG-BASE-01", "익명 프로필 / Instaloader", instaloader)
        }

        if (InstagramRateLimit.shouldSkip() && !InstagramSession.isLoggedIn()) {
            return Result.failure(IllegalStateException(
                "Instagram 요청 제한(429): 자동 재요청을 멈췄습니다. " +
                    InstagramRateLimit.remainingMinutes() + "분 뒤 다시 시도해 주세요."
            ))
        }

        val browser = InstagramWebCatalogEngine.loadBase(
            context = context,
            profileUrl = profileUrl,
            authenticated = false
        )
        if (browser.isSuccess) return browser
        logFailure("IG-BASE-02", "익명 프로필 / WebView", browser)

        val gallery = withContext(Dispatchers.IO) {
            runCatching {
                callGalleryCatalog(
                    profileUrl = profileUrl,
                    cookieHeader = "",
                    sections = "info,avatar"
                )
            }
        }
        if (gallery.isSuccess) return gallery
        logFailure("IG-BASE-03", "익명 프로필 / gallery-dl", gallery)

        val sessionCookie = InstagramSession.cookie().orEmpty()
        if (sessionCookie.isNotBlank()) {
            val skipAuthenticatedInstaloader = InstagramRateLimit.shouldSkip()
            val authenticatedInstaloader = if (skipAuthenticatedInstaloader) {
                Result.failure(IllegalStateException("Instagram 요청 제한 대기 중"))
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { callInstaloaderBase(profileUrl, sessionCookie) }
                }
            }
            if (authenticatedInstaloader.isSuccess) return authenticatedInstaloader
            if (!skipAuthenticatedInstaloader) {
                logFailure("IG-BASE-04", "로그인 프로필 / Instaloader", authenticatedInstaloader)
            }

            val authenticatedBrowser = InstagramWebCatalogEngine.loadBase(
                context = context,
                profileUrl = profileUrl,
                authenticated = true
            )
            if (authenticatedBrowser.isSuccess) return authenticatedBrowser
            logFailure("IG-BASE-05", "로그인 프로필 / WebView", authenticatedBrowser)

            val authenticatedGallery = withContext(Dispatchers.IO) {
                runCatching {
                    callGalleryCatalog(
                        profileUrl = profileUrl,
                        cookieHeader = sessionCookie,
                        sections = "info,avatar"
                    )
                }
            }
            if (authenticatedGallery.isSuccess) return authenticatedGallery
            logFailure("IG-BASE-06", "로그인 프로필 / gallery-dl", authenticatedGallery)

            return Result.failure(
                IllegalStateException(
                    "Instagram 프로필을 불러오지 못했습니다. " +
                        "익명 Instaloader: " + (instaloader.exceptionOrNull()?.message ?: "unknown") +
                        " / 익명 Web: " + (browser.exceptionOrNull()?.message ?: "unknown") +
                        " / 익명 gallery-dl: " + (gallery.exceptionOrNull()?.message ?: "unknown") +
                        " / 로그인 Instaloader: " +
                        (authenticatedInstaloader.exceptionOrNull()?.message ?: "unknown") +
                        " / 로그인 Web: " +
                        (authenticatedBrowser.exceptionOrNull()?.message ?: "unknown") +
                        " / 로그인 gallery-dl: " +
                        (authenticatedGallery.exceptionOrNull()?.message ?: "unknown")
                )
            )
        }

        return Result.failure(
            IllegalStateException(
                "Instagram 프로필을 불러오지 못했습니다. " +
                    "Instaloader: " + (instaloader.exceptionOrNull()?.message ?: "unknown") +
                    " / Web: " + (browser.exceptionOrNull()?.message ?: "unknown") +
                    " / gallery-dl: " + (gallery.exceptionOrNull()?.message ?: "unknown")
            )
        )
    }

    suspend fun loadSection(
        context: Context,
        profileUrl: String,
        section: InstagramCatalogSection,
        isPrivate: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Result<List<InstagramCatalogItem>> {
        val sessionCookie = InstagramSession.cookie().orEmpty()
        if (InstagramRateLimit.shouldSkip() && sessionCookie.isBlank()) {
            return Result.failure(IllegalStateException(
                "Instagram 요청 제한(429): 자동 조회가 잠시 중단됐습니다. 약 " +
                    InstagramRateLimit.remainingMinutes() + "분 뒤 시도해 주세요."
            ))
        }
        val isSessionOnly = section == InstagramCatalogSection.STORIES ||
            section == InstagramCatalogSection.HIGHLIGHTS
        if (isSessionOnly && sessionCookie.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    section.wireName + "는 Instagram 로그인 연결 후 조회할 수 있습니다. " +
                        "계정이 비공개라는 뜻은 아닙니다."
                )
            )
        }
        val sessionPreferred =
            section == InstagramCatalogSection.STORIES ||
                section == InstagramCatalogSection.HIGHLIGHTS ||
                isPrivate

        val attempts = mutableListOf<String>()
        var sawEmptySuccess = false

        suspend fun tryInstaloader(cookie: String, label: String): List<InstagramCatalogItem>? {
            if (InstagramRateLimit.shouldSkip()) {
                attempts += label + ": 429 요청 제한으로 " +
                    InstagramRateLimit.remainingMinutes() + "분간 대기"
                return null
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    callInstaloaderSection(
                        profileUrl = profileUrl,
                        section = section,
                        cookieHeader = cookie
                    )
                }
            }

            result.onFailure {
                attempts += label + ": " + (it.message ?: it.javaClass.simpleName)
                if (InstagramRateLimit.onFailure(it.message)) {
                    DiagnosticLog.record(
                        "IG-RATE-429", "Instagram 요청 제한",
                        "Instaloader에서 429 응답을 받았습니다. 15분 동안 같은 엔진 호출을 중단합니다."
                    )
                }
                DiagnosticLog.failure("IG-SEC-I-" + section.wireName, label, it)
            }

            if (result.isSuccess) {
                val items = result.getOrThrow()
                if (items.isNotEmpty()) {
                    onProgress(items.size)
                    return items
                }
                sawEmptySuccess = true
                attempts += label + ": 0개"
            }
            return null
        }

        suspend fun tryGallery(cookie: String, label: String): List<InstagramCatalogItem>? {
            val result = loadSectionViaGallery(
                profileUrl = profileUrl,
                section = section,
                cookieHeader = cookie
            )

            result.onFailure {
                attempts += label + ": " + (it.message ?: it.javaClass.simpleName)
                DiagnosticLog.failure("IG-SEC-G-" + section.wireName, label, it)
            }

            if (result.isSuccess) {
                val items = result.getOrThrow()
                if (items.isNotEmpty()) {
                    onProgress(items.size)
                    return items
                }
                sawEmptySuccess = true
                attempts += label + ": 0개"
            }
            return null
        }

        if (sessionPreferred && sessionCookie.isNotBlank()) {
            tryInstaloader(sessionCookie, "로그인 Instaloader")?.let {
                return Result.success(it)
            }
        } else {
            tryInstaloader("", "익명 Instaloader")?.let {
                return Result.success(it)
            }

            if (sessionCookie.isNotBlank()) {
                tryInstaloader(sessionCookie, "로그인 Instaloader")?.let {
                    return Result.success(it)
                }
            }
        }

        if (InstagramRateLimit.shouldSkip() && sessionCookie.isBlank()) {
            return Result.failure(IllegalStateException(
                "Instagram 요청 제한(429): 잠시 자동 요청을 멈췄습니다. 약 " +
                    InstagramRateLimit.remainingMinutes() + "분 뒤 다시 시도해 주세요."
            ))
        }

        if (section == InstagramCatalogSection.POSTS ||
            section == InstagramCatalogSection.REELS
        ) {
            val anonymousBrowser = InstagramWebCatalogEngine.loadGrid(
                context = context,
                profileUrl = profileUrl,
                reelsOnly = section == InstagramCatalogSection.REELS,
                authenticated = false,
                onProgress = onProgress
            )

            if (anonymousBrowser.isSuccess) {
                val items = anonymousBrowser.getOrThrow()
                if (items.isNotEmpty()) return Result.success(items)
                sawEmptySuccess = true
                attempts += "익명 Web: 0개"
            } else {
                attempts += "익명 Web: " +
                    (anonymousBrowser.exceptionOrNull()?.message ?: "unknown")
                logFailure("IG-SEC-W-" + section.wireName, "익명 WebView", anonymousBrowser)
            }

            if (sessionCookie.isNotBlank()) {
                val authenticatedBrowser = InstagramWebCatalogEngine.loadGrid(
                    context = context,
                    profileUrl = profileUrl,
                    reelsOnly = section == InstagramCatalogSection.REELS,
                    authenticated = true,
                    onProgress = onProgress
                )

                if (authenticatedBrowser.isSuccess) {
                    val items = authenticatedBrowser.getOrThrow()
                    if (items.isNotEmpty()) return Result.success(items)
                    sawEmptySuccess = true
                    attempts += "로그인 Web: 0개"
                } else {
                    attempts += "로그인 Web: " +
                        (authenticatedBrowser.exceptionOrNull()?.message ?: "unknown")
                    logFailure("IG-SEC-WA-" + section.wireName, "로그인 WebView", authenticatedBrowser)
                }
            }

            tryGallery("", "익명 gallery-dl")?.let {
                return Result.success(it)
            }

            if (sessionCookie.isNotBlank()) {
                tryGallery(sessionCookie, "로그인 gallery-dl")?.let {
                    return Result.success(it)
                }
            }
        } else {
            tryGallery(
                if (sessionCookie.isNotBlank()) sessionCookie else "",
                if (sessionCookie.isNotBlank()) "로그인 gallery-dl" else "익명 gallery-dl"
            )?.let {
                return Result.success(it)
            }
        }

        if ((section == InstagramCatalogSection.STORIES ||
                section == InstagramCatalogSection.HIGHLIGHTS) &&
            sessionCookie.isBlank()
        ) {
            return Result.failure(
                IllegalStateException(
                    section.wireName +
                        " 조회에는 Instagram 로그인이 필요합니다. 앱에서 Instagram 로그인을 먼저 연결해 주세요."
                )
            )
        }

        if (sawEmptySuccess) {
            return Result.failure(
                IllegalStateException(
                    section.wireName +
                        " 조회 결과가 비어 있습니다. Instagram이 목록 요청을 제한했거나 현재 표시할 항목이 없습니다. " +
                        attempts.joinToString(" / ")
                )
            )
        }

        return Result.failure(
            IllegalStateException(
                section.wireName + " 불러오기 실패. " + attempts.joinToString(" / ")
            )
        )
    }

    private suspend fun loadSectionViaGallery(
        profileUrl: String,
        section: InstagramCatalogSection,
        cookieHeader: String
    ): Result<List<InstagramCatalogItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val catalog = callGalleryCatalog(
                profileUrl = profileUrl,
                cookieHeader = cookieHeader,
                sections = section.wireName
            )

            when (section) {
                InstagramCatalogSection.STORIES -> catalog.stories
                InstagramCatalogSection.HIGHLIGHTS -> catalog.highlights
                InstagramCatalogSection.POSTS -> catalog.posts
                InstagramCatalogSection.REELS -> catalog.reels
            }
        }
    }

    private fun callInstaloaderBase(
        profileUrl: String,
        cookieHeader: String
    ): InstagramProfileCatalog {
        val raw = Python.getInstance()
            .getModule("instagram_instaloader")
            .callAttr("base", profileUrl.trim(), cookieHeader)
            .toString()

        val root = JSONObject(raw)
        if (!root.optBoolean("ok", false)) {
            error(root.optString("error").ifBlank { "Instaloader profile lookup failed" })
        }

        return InstagramProfileCatalog(
            username = root.optString("username"),
            fullName = root.optString("full_name"),
            biography = root.optString("biography"),
            isPrivate = root.optBoolean("is_private", false),
            avatar = root.optJSONObject("avatar")?.let(::parseItem)
        )
    }

    private fun callInstaloaderSection(
        profileUrl: String,
        section: InstagramCatalogSection,
        cookieHeader: String
    ): List<InstagramCatalogItem> {
        val raw = Python.getInstance()
            .getModule("instagram_instaloader")
            .callAttr(
                "section",
                profileUrl.trim(),
                section.wireName,
                cookieHeader
            )
            .toString()

        val root = JSONObject(raw)
        if (!root.optBoolean("ok", false)) {
            error(root.optString("error").ifBlank { "Instaloader section lookup failed" })
        }
        return parseItems(root.optJSONArray("items"))
    }

    private fun callGalleryCatalog(
        profileUrl: String,
        cookieHeader: String,
        sections: String
    ): InstagramProfileCatalog {
        val raw = Python.getInstance()
            .getModule("instagram_gallery")
            .callAttr(
                "profile_catalog",
                profileUrl.trim(),
                cookieHeader,
                0,
                sections
            )
            .toString()

        val root = JSONObject(raw)
        val errors = parseErrors(root.optJSONObject("errors"))
        val profile = root.optJSONObject("profile") ?: JSONObject()

        if (sections.contains("info") &&
            profile.length() == 0 &&
            errors["info"].isNullOrBlank().not()
        ) {
            error(errors["info"] ?: "프로필을 불러오지 못했습니다.")
        }

        return InstagramProfileCatalog(
            username = root.optString("username"),
            fullName = profile.optString("full_name"),
            biography = profile.optString("biography"),
            isPrivate = profile.optBoolean("is_private", false),
            avatar = root.optJSONObject("avatar")?.let(::parseItem),
            stories = parseItems(root.optJSONArray("stories")),
            highlights = parseItems(root.optJSONArray("highlights")),
            posts = parseItems(root.optJSONArray("posts")),
            reels = parseItems(root.optJSONArray("reels")),
            errors = errors
        )
    }

    private fun parseItems(array: JSONArray?): List<InstagramCatalogItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(parseItem(obj))
            }
        }
    }

    private fun parseItem(obj: JSONObject): InstagramCatalogItem =
        InstagramCatalogItem(
            id = obj.optString("id"),
            kind = obj.optString("kind"),
            title = obj.optString("title"),
            targetUrl = obj.optString("target_url"),
            previewUrl = obj.optString("preview_url"),
            isVideo = obj.optBoolean("is_video", false),
            date = obj.optString("date"),
            count = obj.optInt("count", 1)
        )

    private fun parseErrors(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key))
            }
        }
    }
}
