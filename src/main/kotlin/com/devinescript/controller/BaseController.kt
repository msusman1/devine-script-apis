package com.devinescript.controller

import io.ktor.application.*
import io.ktor.response.*


open class BaseController(protected val call: ApplicationCall) {


    suspend fun respondSuccess(data: Any? = null, msg: String? = null) {

        call.respond(Response(true, msg, data))

    }



    suspend fun respondError(msg: String? = null, data: Any? = null) {
        call.respond(Response(false, msg, data))
    }


    data class Response(val success: Boolean, val msg: String? = null, val data: Any? = null)

}

class ResponseException(val msg: String? = null) : Exception(msg)