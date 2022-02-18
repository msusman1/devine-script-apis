package com.devinescript.plugins

import com.devinescript.controller.DevineController
import io.ktor.application.*
import io.ktor.features.*

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.configureRouting() {



    routing {
        static("/devine_script") {
            resources("devine_script")
        }
        get("/parse") {
            DevineController(this.call).fetchDemonstration()

        }
        get("/") {
            call.respondText("Hello World!")
        }
        get("/demo") {
//            DevineController(this.call).parse()
        }
        get("/displayDomain") {
          DevineController(this.call).displayDemonstration()
        }
        get("/rename") {
//            DevineController(this.call).renameFiles()
        }
        get("/branches") {
            DevineController(this.call).fetchBranches()

        }
        get("/domains") {
            DevineController(this.call).fetchDomains()

        }


        // Static plugin. Try to access `/static/index.html`
        static("/front-end") {
            resources("front-end")
        }
    }
}
class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
