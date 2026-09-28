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
    val stories: List<InstagramCatalogItem>,
    val highlights: List<InstagramCatalogItem>,
    val posts: List<InstagramCatalogItem>,
    val reels: List<InstagramCatalogItem>,
    val errors: Map<String, String>
)

object InstagramProfileCatalogEngine {
    suspend fun load(profileUrl: String): Result<InstagramProfileCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                val anonymous = callCatalog(
                    profileUrl = profileUrl,
                    cookieHeader = "",
                    sections = "all"
                )

                var merged = anonymous
                val cookie = InstagramSession.cookie()

                if (!cookie.isNullOrBlank()) {
                    val needsProtected = anonymous.stories.isEmpty() || anonymous.highlights.isEmpty()
                    if (needsProtected) {
                        val protected = callCatalog(
                            profileUrl = profileUrl,
                            cookieHeader = cookie,
                            sections = "stories,highlights"
                        )
                        merged = merged.copy(
                            stories = protected.stories.ifEmpty { merged.stories },
                            highlights = protected.highlights.ifEmpty { merged.highlights },
                            errors = merged.errors + protected.errors
                        )
                    }

                    val needsPrivateFeed =
                        anonymous.isPrivate && anonymous.posts.isEmpty() && anonymous.reels.isEmpty()

                    if (needsPrivateFeed) {
                        val privateFeed = callCatalog(
                            profileUrl = profileUrl,
                            cookieHeader = cookie,
                            sections = "info,avatar,posts,reels"
                        )
                        merged = merged.copy(
                            fullName = privateFeed.fullName.ifBlank { merged.fullName },
                            biography = privateFeed.biography.ifBlank { merged.biography },
                            avatar = privateFeed.avatar ?: merged.avatar,
                            posts = privateFeed.posts.ifEmpty { merged.posts },
                            reels = privateFeed.reels.ifEmpty { merged.reels },
                            errors = merged.errors + privateFeed.errors
                        )
                    }
                }

                merged
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
                24,
                sections
            )
            .toString()

        val root = JSONObject(raw)
        if (!root.optBoolean("ok", true) && root.optJSONObject("profile")?.length() == 0) {
            val message = root.optJSONObject("errors")
                ?.optString("info")
                ?.takeIf { it.isNotBlank() }
                ?: "프로필을 불러오지 못했습니다."
            error(message)
        }

        val profile = root.optJSONObject("profile") ?: JSONObject()
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
            errors = parseErrors(root.optJSONObject("errors"))
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
