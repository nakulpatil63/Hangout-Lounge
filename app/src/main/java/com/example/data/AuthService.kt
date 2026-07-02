package com.example.data

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AuthService {
    private const val TAG = "AuthService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Map wss:// server url to https:// or ws:// to http:// for REST requests
    private fun getHttpUrl(wsUrl: String): String {
        return wsUrl
            .replace("wss://", "https://")
            .replace("ws://", "http://")
    }

    /**
     * Register a new user securely.
     */
    suspend fun register(serverUrl: String, username: String, secret: String, avatar: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val httpBase = getHttpUrl(serverUrl)
                val url = "$httpBase/api/auth/register"
                
                val payload = JSONObject().apply {
                    put("username", username)
                    put("password", secret)
                    put("avatarIndex", avatar)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Result.success("Registration successful! Proceed to login.")
                    } else {
                        val errorMsg = try {
                            JSONObject(bodyStr).optString("error", "Registration failed")
                        } catch (e: Exception) {
                            "Registration failed with code ${response.code}"
                        }
                        Result.failure(Exception(errorMsg))
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Registration connection failure", e)
                Result.failure(Exception("Cannot connect to server. Check network connection and URL."))
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed unexpected error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Login user and obtain access token + refresh token.
     */
    suspend fun login(serverUrl: String, username: String, secret: String): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val httpBase = getHttpUrl(serverUrl)
                val url = "$httpBase/api/auth/login"

                val deviceInfo = "Android API ${Build.VERSION.SDK_INT} (${Build.MODEL})"
                val payload = JSONObject().apply {
                    put("username", username)
                    put("password", secret)
                    put("deviceInfo", deviceInfo)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Result.success(JSONObject(bodyStr))
                    } else {
                        val errorMsg = try {
                            JSONObject(bodyStr).optString("error", "Invalid credentials")
                        } catch (e: Exception) {
                            "Login failed with code ${response.code}"
                        }
                        Result.failure(Exception(errorMsg))
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Login connection failure", e)
                Result.failure(Exception("Cannot connect to server. Check server URL."))
            } catch (e: Exception) {
                Log.e(TAG, "Login failure unexpected error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Perform token rotation refresh.
     */
    suspend fun refresh(serverUrl: String, refreshToken: String): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val httpBase = getHttpUrl(serverUrl)
                val url = "$httpBase/api/auth/refresh"

                val payload = JSONObject().apply {
                    put("refreshToken", refreshToken)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Result.success(JSONObject(bodyStr))
                    } else {
                        val errorMsg = try {
                            JSONObject(bodyStr).optString("error", "Session expired")
                        } catch (e: Exception) {
                            "Refresh failed"
                        }
                        Result.failure(Exception(errorMsg))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Perform secure logout.
     */
    suspend fun logout(serverUrl: String, accessToken: String, refreshToken: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val httpBase = getHttpUrl(serverUrl)
                val url = "$httpBase/api/auth/logout"

                val payload = JSONObject().apply {
                    put("refreshToken", refreshToken)
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    Result.success(response.isSuccessful)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                Result.failure(e)
            }
        }
    }
}
