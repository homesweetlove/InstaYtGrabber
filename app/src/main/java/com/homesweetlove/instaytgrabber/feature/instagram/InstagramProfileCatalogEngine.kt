package com.homesweetlove.instaytgrabber.feature.instagram

import android.content.Context
import com.chaquo.python.Python
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
    suspend fun loadBase(
        context: Context,
        profileUrl: String
    ): Result<InstagramProfileCatalog> {
        val instaloader = withContext(Dispatchers.IO) {
            runCatching { callInstaloaderBase(profileUrl, "") }
        }
        if (instaloader.isSuccess) return instaloader

        val browser = InstagramWebCatalogEngine.loadBase(
            context = context,
            profileUrl = profileUrl,
            authenticated = false
        )
        if (browser.isSuccess) return browser

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

        val sessionCookie = InstagramSession.cookie().orEmpty()
        if (sessionCookie.isNotBlank()) {
            val authenticatedInstaloader = withContext(Dispatchers.IO) {
                runCatching { callInstaloaderBase(profileUrl, sessionCookie) }
            }
            if (authenticatedInstaloader.isSuccess) return authenticatedInstaloader

            val authenticatedBrowser = InstagramWebCatalogEngine.loadBase(
                context = context,
                profileUrl = profileUrl,
                authenticated = true
            )
            if (authenticatedBrowser.isSuccess) return authenticatedBrowser

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
        val requiresSession =
            section == InstagramCatalogSection.STORIES ||
                section == InstagramCatalogSection.HIGHLIGHTS ||
                isPrivate

        val firstCookie = if (requiresSession) sessionCookie else ""

        val instaloader = withContext(Dispatchers.IO) {
            runCatching {
                callInstaloaderSection(
                    profileUrl = profileUrl,
                    section = section,
                    cookieHeader = firstCookie
                )
            }
        }
        if (instaloader.isSuccess) {
            val items = instaloader.getOrThrow()
            onProgress(items.size)
            return instaloader
        }

        if (!requiresSession && sessionCookie.isNotBlank()) {
            val authenticatedInstaloader = withContext(Dispatchers.IO) {
                runCatching {
                    callInstaloaderSection(
                        profileUrl = profileUrl,
                        section = section,
                        cookieHeader = sessionCookie
                    )
                }
            }
            if (authenticatedInstaloader.isSuccess) {
                val items = authenticatedInstaloader.getOrThrow()
                onProgress(items.size)
                return authenticatedInstaloader
            }
        }

        if (section == InstagramCatalogSection.POSTS ||
            section == InstagramCatalogSection.REELS
        ) {
            val browser = InstagramWebCatalogEngine.loadGrid(
                context = context,
                profileUrl = profileUrl,
                reelsOnly = section == InstagramCatalogSection.REELS,
                authenticated = requiresSession && sessionCookie.isNotBlank(),
                onProgress = onProgress
            )

            if (browser.isSuccess && browser.getOrNull().orEmpty().isNotEmpty()) {
                return browser
            }

            val gallery = loadSectionViaGallery(
                profileUrl = profileUrl,
                section = section,
                cookieHeader = if (requiresSession) sessionCookie else ""
            )
            if (gallery.isSuccess) return gallery

            if (!requiresSession && sessionCookie.isNotBlank()) {
                val authenticatedGallery = loadSectionViaGallery(
                    profileUrl = profileUrl,
                    section = section,
                    cookieHeader = sessionCookie
                )
                if (authenticatedGallery.isSuccess) return authenticatedGallery
            }

            if (browser.isSuccess) return browser

            return Result.failure(
                IllegalStateException(
                    section.wireName + " 불러오기 실패. " +
                        "Instaloader: " + (instaloader.exceptionOrNull()?.message ?: "unknown") +
                        " / Web: " + (browser.exceptionOrNull()?.message ?: "unknown") +
                        " / gallery-dl: " + (gallery.exceptionOrNull()?.message ?: "unknown")
                )
            )
        }

        val gallery = loadSectionViaGallery(
            profileUrl = profileUrl,
            section = section,
            cookieHeader = sessionCookie
        )
        if (gallery.isSuccess) return gallery

        return Result.failure(
            IllegalStateException(
                section.wireName + " 불러오기 실패. " +
                    "Instaloader: " + (instaloader.exceptionOrNull()?.message ?: "unknown") +
                    " / gallery-dl: " + (gallery.exceptionOrNull()?.message ?: "unknown")
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
