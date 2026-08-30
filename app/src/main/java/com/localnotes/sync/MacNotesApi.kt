package com.localnotes.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class RemoteFolder(
    val appleId: String,
    val name: String,
    val accountName: String?,
    val accountAppleId: String?,
)

data class RemoteNoteMeta(
    val appleId: String,
    val title: String,
    val folderAppleId: String?,
    val folderName: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val passwordProtected: Boolean,
)

data class RemoteNote(
    val appleId: String,
    val title: String,
    val folderAppleId: String?,
    val folderName: String?,
    val html: String,
    val plaintext: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val passwordProtected: Boolean,
)

data class RemoteCatalog(
    val folders: List<RemoteFolder>,
    val notes: List<RemoteNoteMeta>,
)

class MacNotesApi(
    private val host: String,
    private val port: Int,
    private val token: String? = null,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val root = "http://$host:$port"

    fun hello(): JSONObject = get("/v1/hello", authed = token != null)

    fun revision(): String = get("/v1/revision").optString("revision")

    fun liveNote(): RemoteNote? {
        val obj = get("/v1/live")
        if (!obj.optBoolean("active") && !obj.has("appleId")) return null
        val appleId = obj.optString("appleId")
        if (appleId.isBlank()) return null
        return RemoteNote(
            appleId = appleId,
            title = obj.optString("title"),
            folderAppleId = obj.optStringOrNull("folderAppleId"),
            folderName = obj.optStringOrNull("folderName"),
            html = obj.optString("html"),
            plaintext = obj.optString("plaintext"),
            createdAt = parseTime(obj.optString("createdAt")),
            modifiedAt = parseTime(obj.optString("modifiedAt")),
            passwordProtected = obj.optBoolean("passwordProtected"),
        )
    }

    fun pair(pin: String): String {
        val body = JSONObject().put("pin", pin)
        val response = post("/v1/pair", body, authed = false)
        return response.getString("token")
    }

    fun catalog(): RemoteCatalog {
        val obj = get("/v1/catalog")
        val folders = obj.optJSONArray("folders").objects().map {
            RemoteFolder(
                appleId = it.getString("appleId"),
                name = it.getString("name"),
                accountName = it.optStringOrNull("accountName"),
                accountAppleId = it.optStringOrNull("accountAppleId"),
            )
        }
        val notes = obj.optJSONArray("notes").objects().map {
            RemoteNoteMeta(
                appleId = it.getString("appleId"),
                title = it.optString("title"),
                folderAppleId = it.optStringOrNull("folderAppleId"),
                folderName = it.optStringOrNull("folderName"),
                createdAt = parseTime(it.optString("createdAt")),
                modifiedAt = parseTime(it.optString("modifiedAt")),
                passwordProtected = it.optBoolean("passwordProtected"),
            )
        }
        return RemoteCatalog(folders, notes)
    }

    fun getNote(appleId: String): RemoteNote {
        val it = get("/v1/notes?id=" + urlEncode(appleId))
        return RemoteNote(
            appleId = it.getString("appleId"),
            title = it.optString("title"),
            folderAppleId = it.optStringOrNull("folderAppleId"),
            folderName = it.optStringOrNull("folderName"),
            html = it.optString("html"),
            plaintext = it.optString("plaintext"),
            createdAt = parseTime(it.optString("createdAt")),
            modifiedAt = parseTime(it.optString("modifiedAt")),
            passwordProtected = it.optBoolean("passwordProtected"),
        )
    }

    fun upsertNote(appleId: String?, folderAppleId: String?, html: String): Pair<String, Long> {
        val body = JSONObject()
            .put("html", html)
        if (appleId != null) body.put("appleId", appleId)
        if (folderAppleId != null) body.put("folderAppleId", folderAppleId)
        val result = put("/v1/notes", body)
        return result.getString("appleId") to parseTime(result.optString("modifiedAt"))
    }

    fun deleteNote(appleId: String) {
        delete("/v1/notes?id=" + urlEncode(appleId))
    }

    fun createFolder(name: String, accountAppleId: String? = null): String {
        val body = JSONObject().put("name", name)
        if (accountAppleId != null) body.put("accountAppleId", accountAppleId)
        return post("/v1/folders", body).getString("appleId")
    }

    private fun get(path: String, authed: Boolean = true): JSONObject =
        execute(Request.Builder().url(root + path).get(), authed)

    private fun post(path: String, body: JSONObject, authed: Boolean = true): JSONObject =
        execute(
            Request.Builder().url(root + path).post(body.toString().toRequestBody(jsonType)),
            authed,
        )

    private fun put(path: String, body: JSONObject): JSONObject =
        execute(
            Request.Builder().url(root + path).put(body.toString().toRequestBody(jsonType)),
            authed = true,
        )

    private fun delete(path: String): JSONObject =
        execute(Request.Builder().url(root + path).delete(), authed = true)

    private fun execute(builder: Request.Builder, authed: Boolean): JSONObject {
        if (authed) {
            val value = token ?: throw NeedsPinException()
            builder.header("X-Notes-Token", value)
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw NeedsPinException()
            if (response.code == 403) throw WrongPinException()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}"
                throw IOException(message)
            }
            if (text.isBlank()) return JSONObject()
            return JSONObject(text)
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONArray?.objects(): List<JSONObject> {
    val array = this ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) add(array.getJSONObject(i))
    }
}

private fun parseTime(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}

private fun urlEncode(value: String): String =
    java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
