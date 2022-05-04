package com.devinescript

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.devinescript.plugins.*

fun main() {
    embeddedServer(Netty, port = 8181, host = "0.0.0.0") {
        configureRouting()
        configureSerialization()
        configureMonitoring()
    }.start(wait = true)
}
