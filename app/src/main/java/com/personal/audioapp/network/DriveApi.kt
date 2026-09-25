package com.personal.audioapp.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * Minimal slice of the Google Drive v3 REST API.
 *
 * Uploads use the **resumable** protocol instead of `uploadType=multipart`,
 * because a multipart upload is capped at 5 MB by Google (~10 minutes of
 * 64 kbps audio). The flow is two calls:
 *
 * 1. [startResumableUpload] — POST the file metadata; Google replies `200` with
 *    the session URL in the `Location` header.
 * 2. [uploadToSession] — PUT the bytes to that URL. The session URL already
 *    carries its own `upload_id`, so it needs no `Authorization` header.
 *
 * The access token always travels through the `Authorization` header, which
 * keeps token refresh logic in one place.
 */
interface DriveApi {

    @POST("upload/drive/v3/files?uploadType=resumable&fields=id,name,mimeType,size")
    suspend fun startResumableUpload(
        @Header("Authorization") bearer: String,
        @Header("X-Upload-Content-Type") contentType: String,
        @Header("X-Upload-Content-Length") contentLength: Long,
        @Body metadata: CreateFileRequest
    ): Response<ResponseBody>

    @PUT
    suspend fun uploadToSession(
        @Url sessionUrl: String,
        @Body file: RequestBody
    ): Response<DriveFile>

    /** Creates a normal Drive folder, used when the user has no destination id. */
    @POST("drive/v3/files?fields=id,name")
    suspend fun createFolder(
        @Header("Authorization") bearer: String,
        @Body body: CreateFolderRequest
    ): DriveFile
}

/** Metadata of the file the app is about to upload. */
data class CreateFileRequest(
    val name: String,
    val mimeType: String = "audio/mpeg",
    /** Empty for the Drive root, otherwise the id of the target folder. */
    val parents: List<String>? = null
)

/** Gson model for a Drive file resource (all fields nullable on purpose). */
data class DriveFile(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val size: Long? = null
)

data class CreateFolderRequest(
    val name: String,
    val mimeType: String = "application/vnd.google-apps.folder",
    val parents: List<String>? = null
)
