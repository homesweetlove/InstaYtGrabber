package com.homesweetlove.instaytgrabber.feature.instagram

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
    suspend fun loadBase(profileUrl: String): Result<InstagramProfileCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                val anonymous = callCatalog(
                    profileUrl = profileUrl,
                    cookieHeader = "",
                    sections = "info,avatar"
                )

                if (anonymous.username.isNotBlank() && anonymous.errors["info"].isNullOrBlank()) {
                    anonymous
                } else {
                    val cookie = InstagramSession.cookie()
                    if (cookie.isNullOrBlank()) {
                        anonymous
                    } else {
                        callCatalog(
                            profileUrl = profileUrl,
                            cookieHeader = cookie,
                            sections = "info,avatar"
                        )
                    }
                }
            }
        }

    suspend fun loadSection(
        profileUrl: String,
        section: InstagramCatalogSection,
        isPrivate: Boolean
    ): Result<List<InstagramCatalogItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = if (isPrivate) InstagramSession.cookie().orEmpty() else ""
            val catalog = callCatalog(
                profileUrl = profileUrl,
                cookieHeader = cookie,
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

    private fun callCatalog(
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

        if (sections.contains("info") && profile.length() == 0 && errors["info"].isNullOrBlank().not()) {
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
