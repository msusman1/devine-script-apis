package com.devinescript.utils

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import io.ktor.http.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object OkhttpUtils {
    val client = OkHttpClient()
    suspend fun translateOld(query: String, targetLang: String): String? {
        val url =
            "https://www.googleapis.com/language/translate/v2?key=AIzaSyAmjdk5DfKvQyzrk9Jb2HM_cJBESmTfI38&q=Pakistan is our homeland&source=en&target=ur,ar"
        val queryParams = Parameters.build {
            append("key", "AIzaSyD50tr7k4cQYbyfmW4AIxgGNmi4gPYs6jE")
            append("q", query)
            append("source", "en")
            append("target", targetLang)
        }

        val httpUrl = HttpUrl.Builder().apply {
            scheme("https")
            host("www.googleapis.com")
            encodedPath("/language/translate/v2")
            encodedQuery(queryParams.formUrlEncode())
        }.build()


        val request = Request.Builder().url(httpUrl).build()
        return suspendCoroutine { continuation ->
            val response = client.newCall(request).execute()
            var translatedText: String? = null
            val body = response.body
            if (body != null) {
                val bodyStr: String = body.string()
                val translationResponse = Gson().fromJson(bodyStr, TranslationResponse::class.java)
                if (translationResponse.data != null) {
                    translatedText = translationResponse.data?.translations?.firstOrNull()?.translatedText
                    println("Translated: $query => $translatedText")
                } else {
                    val error = translationResponse.error?.message ?: "Uknown Error"
                    println("okhttputils: error :" + error)
                }
            } else {
                println("okhttputils: " + "Body is null")
            }


            continuation.resume(translatedText)
        }

    }

    suspend fun translate(query: String, targetLang: String): String? {

        val mediaType = "text/plain".toMediaType()
        val requestBody = "".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${targetLang}&dt=t&q=${query}")
            .post(requestBody).build()


        return suspendCoroutine { continuation ->
            val response = client.newCall(request).execute()
            val body = response.body
            if (body != null) {
                val bodyStr: String = body.string()
                val gson = Gson()
                val jsonArray = gson.fromJson(bodyStr, JsonArray::class.java)
                val translatedText = jsonArray[0].asJsonArray[0].asJsonArray[0].asString
                println("Translated: $query => $translatedText")
                continuation.resume(translatedText)
            } else {
                println("okhttputils: " + "Body is null")
                continuation.resume(null)
            }

        }
    }

    suspend fun downloadFile(url: String, name: String): String? {
        val extention = url.substringAfterLast(".")
        val file = File("src/main/resources/devine_script/images/${name}.$extention")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body
            if (body != null) {
                val inputStream = body.byteStream()
                val outputStream = file.outputStream()
                outputStream.write(inputStream.readAllBytes())
                outputStream.flush()
                inputStream.close()
                outputStream.close()

                return file.path.replace("\\", "/").replace("src/main/resources/", "")
            } else {
                print("okhttputils: " + "Body is null")
            }

        } else {
            print("okhttputils: " + response.message)
        }

        return null
    }

}

data class TranslationResponse(
    val error: Error?, var data: Data?
)

data class Error(

    @SerializedName("code") var code: Int? = null, @SerializedName("message") var message: String? = null

)

data class Data(

    @SerializedName("translations") var translations: List<Translations>?

)

data class Translations(

    @SerializedName("translatedText") var translatedText: String? = null

)