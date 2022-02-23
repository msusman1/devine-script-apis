package com.devinescript.utils

import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO

object OkhttpUtils {
    val client = OkHttpClient()
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

                return file.path.replace("\\","/").replace("src/main/resources/","")
            } else {
                print("okhttputils: " + "Body is null")
            }

        } else {
            print("okhttputils: " + response.message)
        }

        return null
    }

}