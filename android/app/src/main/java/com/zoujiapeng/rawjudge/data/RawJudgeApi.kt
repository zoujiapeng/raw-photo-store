package com.zoujiapeng.rawjudge.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.zoujiapeng.rawjudge.BuildConfig
import com.zoujiapeng.rawjudge.domain.AuditEvent
import com.zoujiapeng.rawjudge.domain.BlindPair
import com.zoujiapeng.rawjudge.domain.LicenseGrant
import com.zoujiapeng.rawjudge.domain.LicenseType
import com.zoujiapeng.rawjudge.domain.ModerationStatus
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.ReviewMetrics
import com.zoujiapeng.rawjudge.domain.SessionUser
import com.zoujiapeng.rawjudge.domain.UploadDraft
import com.zoujiapeng.rawjudge.domain.Work
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class ApiException(val statusCode: Int, message: String) : Exception(message)

data class AuthSession(val token: String, val user: SessionUser)

class RawJudgeApi(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val cacheDir: File = context.cacheDir.resolve("downloads").apply { mkdirs() }
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    var token: String? = null

    fun register(displayName: String = "Android 摄影者"): AuthSession {
        val body = JSONObject().put("display_name", displayName)
        val json = requestObject("POST", "/v1/auth/anonymous", body, authenticated = false)
        return AuthSession(json.getString("access_token"), parseUser(json.getJSONObject("user")))
    }

    fun me(): SessionUser = parseUser(requestObject("GET", "/v1/me"))

    fun updateProfile(displayName: String, handle: String, externalUrl: String?): SessionUser {
        val body = JSONObject()
            .put("display_name", displayName)
            .put("handle", handle)
        if (!externalUrl.isNullOrBlank()) body.put("external_url", externalUrl)
        return parseUser(requestObject("PATCH", "/v1/me", body))
    }

    fun listWorks(partition: Partition? = null): List<Work> {
        val suffix = partition?.let { "?partition=${it.api}" } ?: ""
        val array = requestArray("GET", "/v1/works$suffix", authenticated = token != null)
        return buildList {
            for (index in 0 until array.length()) add(parseWork(array.getJSONObject(index)))
        }
    }

    fun favorite(workId: Long, favorite: Boolean): Work = parseWork(
        requestObject(
            "POST",
            "/v1/works/$workId/favorite",
            JSONObject().put("favorite", favorite)
        )
    )

    fun addReview(workId: Long, body: String, score: Int): Review = parseReview(
        requestObject(
            "POST",
            "/v1/works/$workId/reviews",
            JSONObject().put("body", body).put("score", score)
        )
    )

    fun appeal(workId: Long, reason: String) {
        requestObject(
            "POST",
            "/v1/works/$workId/appeals",
            JSONObject().put("reason", reason)
        )
    }

    fun report(workId: Long, reason: String) {
        requestObject(
            "POST",
            "/v1/works/$workId/reports",
            JSONObject().put("reason", reason)
        )
    }

    fun purchase(workId: Long, type: LicenseType): LicenseGrant = parseLicense(
        requestObject("POST", "/v1/works/$workId/licenses/${type.api}", JSONObject())
    )

    fun blindPair(sequence: Int): BlindPair {
        val json = requestObject("GET", "/v1/blind/pair?sequence=$sequence")
        return BlindPair(
            left = parseBlindWork(json.getJSONObject("left")),
            right = parseBlindWork(json.getJSONObject("right")),
            sequence = json.getInt("sequence")
        )
    }

    fun blindVote(pair: BlindPair, winnerId: Long?) {
        val body = JSONObject()
            .put("left_work_id", pair.left.id)
            .put("right_work_id", pair.right.id)
        if (winnerId == null) body.put(JSONObject.NULL) else body.put("winner_work_id", winnerId)
        requestObject("POST", "/v1/blind/vote", body)
    }

    fun upload(draft: UploadDraft): Work {
        val imageUri = draft.photoUri?.let(Uri::parse)
            ?: throw IllegalArgumentException("请选择展示图")
        val boundary = "RAWJudge-${System.nanoTime()}"
        val connection = open("POST", "/v1/works", authenticated = true).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(1024 * 1024)
        }
        BufferedOutputStream(connection.outputStream).use { output ->
            fun text(name: String, value: String) {
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                output.write(value.toByteArray())
                output.write("\r\n".toByteArray())
            }
            fun file(name: String, uri: Uri, filename: String) {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"$name\"; filename=\"${filename.replace("\"", "_")}\"\r\n".toByteArray()
                )
                output.write("Content-Type: $mime\r\n\r\n".toByteArray())
                resolver.openInputStream(uri)?.use { input -> input.copyTo(output, 1024 * 1024) }
                    ?: throw IllegalArgumentException("无法读取 $filename")
                output.write("\r\n".toByteArray())
            }
            text("title", draft.title.ifBlank { draft.imageFileName ?: "未命名作品" })
            text("description", draft.description)
            text("allow_preview", draft.allowPreviewDownload.toString())
            text("allow_raw", draft.allowRawLicense.toString())
            text("preview_price", draft.previewPrice.toString())
            text("raw_price", draft.rawPrice.toString())
            file("image", imageUri, draft.imageFileName ?: "photo.jpg")
            draft.rawUri?.let { rawUri ->
                file("raw", Uri.parse(rawUri), draft.rawFileName ?: "photo.dng")
            }
            output.write("--$boundary--\r\n".toByteArray())
        }
        return parseWork(readObject(connection))
    }

    fun download(workId: Long, raw: Boolean, suggestedName: String): File {
        val endpoint = if (raw) "raw" else "original"
        val connection = open("GET", "/v1/works/$workId/$endpoint", authenticated = true)
        val status = connection.responseCode
        if (status !in 200..299) throw apiError(connection, status)
        val safeName = suggestedName.replace(Regex("[^A-Za-z0-9._-]+"), "_").takeLast(150)
        val destination = cacheDir.resolve(safeName.ifBlank { "rawjudge-$workId.bin" })
        BufferedInputStream(connection.inputStream).use { input ->
            destination.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun requestObject(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true
    ): JSONObject {
        val connection = open(method, path, authenticated)
        if (body != null && method != "GET") {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
        return readObject(connection)
    }

    private fun requestArray(
        method: String,
        path: String,
        authenticated: Boolean
    ): JSONArray {
        val connection = open(method, path, authenticated)
        val status = connection.responseCode
        val content = readText(connection, status)
        if (status !in 200..299) throw parseError(status, content)
        return JSONArray(content)
    }

    private fun readObject(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val content = readText(connection, status)
        if (status !in 200..299) throw parseError(status, content)
        return JSONObject(content)
    }

    private fun open(method: String, path: String, authenticated: Boolean): HttpURLConnection {
        val target = if (path.startsWith("http://") || path.startsWith("https://")) path else "$baseUrl$path"
        return (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
        }
    }

    private fun readText(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun apiError(connection: HttpURLConnection, status: Int): ApiException =
        parseError(status, readText(connection, status))

    private fun parseError(status: Int, content: String): ApiException {
        val message = runCatching { JSONObject(content).optString("detail") }
            .getOrNull()
            .takeUnless { it.isNullOrBlank() }
            ?: content.take(300).ifBlank { "HTTP $status" }
        return ApiException(status, message)
    }

    private fun parseUser(json: JSONObject): SessionUser = SessionUser(
        id = json.getLong("id"),
        displayName = json.getString("display_name"),
        handle = json.getString("handle"),
        externalUrl = json.nullableString("external_url"),
        reviewerTrust = json.optDouble("reviewer_trust", 0.22).toFloat()
    )

    private fun parseWork(json: JSONObject): Work {
        val reviews = json.optJSONArray("reviews")?.mapObjects(::parseReview).orEmpty()
        val audits = json.optJSONArray("audit")?.mapObjects { item ->
            AuditEvent(
                id = item.getLong("id"),
                kind = item.getString("kind"),
                message = item.getString("message"),
                createdAt = item.instant("created_at")
            )
        }.orEmpty()
        val licenses = json.optJSONArray("licenses")?.mapObjects(::parseLicense).orEmpty()
        return Work(
            id = json.getLong("id"),
            ownerId = json.nullableLong("owner_id"),
            title = json.getString("title"),
            description = json.optString("description"),
            authorName = json.getString("author_name"),
            handle = json.getString("handle"),
            externalUrl = json.nullableString("external_url"),
            imageUrl = json.nullableString("image_url")?.let(::absoluteUrl),
            imageFileName = json.nullableString("image_name"),
            rawFileName = json.nullableString("raw_name"),
            rawVerified = json.optBoolean("raw_verified"),
            score = json.optDouble("score"),
            confidence = json.optDouble("confidence"),
            partition = Partition.fromApi(json.optString("partition")),
            moderationStatus = ModerationStatus.fromApi(json.optString("moderation_status")),
            moderationSummary = json.optString("moderation_summary"),
            favorites = json.optInt("favorites"),
            downloads = json.optInt("downloads"),
            followers = json.optInt("followers"),
            ratings = json.optInt("ratings"),
            isFavorite = json.optBoolean("is_favorite"),
            isOwner = json.optBoolean("is_owner"),
            isOfficialSample = json.optBoolean("official_sample"),
            canDownloadOriginal = json.optBoolean("can_download_original"),
            canDownloadRaw = json.optBoolean("can_download_raw"),
            reviews = reviews,
            auditTrail = audits,
            licenses = licenses,
            paletteSeed = json.getLong("id").toInt()
        )
    }

    private fun parseBlindWork(json: JSONObject): Work = Work(
        id = json.getLong("id"),
        title = "盲评作品",
        description = "",
        authorName = "隐藏",
        handle = "@blind",
        imageUrl = json.nullableString("image_url")?.let(::absoluteUrl),
        rawVerified = json.optBoolean("raw_verified"),
        score = 0.0,
        confidence = json.optDouble("confidence"),
        partition = Partition.REVIEW,
        moderationStatus = ModerationStatus.REVIEWING,
        moderationSummary = "盲评时隐藏身份和社交数据。"
    )

    private fun parseReview(json: JSONObject): Review {
        val metrics = json.getJSONObject("metrics")
        return Review(
            id = json.getLong("id"),
            reviewerUserId = json.nullableLong("reviewer_user_id"),
            author = json.getString("author_name"),
            body = json.getString("body"),
            score = json.getInt("score"),
            metrics = ReviewMetrics(
                relevance = metrics.optDouble("relevance").toFloat(),
                professional = metrics.optDouble("professional").toFloat(),
                technical = metrics.optDouble("technical").toFloat(),
                objective = metrics.optDouble("objective").toFloat(),
                constructive = metrics.optDouble("constructive").toFloat()
            ),
            createdAt = json.instant("created_at"),
            isAi = json.optBoolean("is_ai"),
            reviewerTrust = json.optDouble("reviewer_trust", 0.22).toFloat(),
            scoreCounted = json.optBoolean("score_counted")
        )
    }

    private fun parseLicense(json: JSONObject): LicenseGrant = LicenseGrant(
        id = json.getLong("id"),
        type = LicenseType.fromApi(json.getString("license_type")),
        price = json.optDouble("price"),
        terms = json.optString("terms"),
        granted = json.optBoolean("granted")
    )

    private fun absoluteUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value else "$baseUrl$value"

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name) || !has(name)) null else getLong(name)

    private fun JSONObject.instant(name: String): Instant =
        runCatching { Instant.parse(getString(name)) }.getOrDefault(Instant.now())

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList { for (index in 0 until length()) add(transform(getJSONObject(index))) }
}
