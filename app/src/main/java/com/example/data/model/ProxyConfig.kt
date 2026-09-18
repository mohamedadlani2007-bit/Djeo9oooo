package com.example.data.model

data class ProxyConfig(
    val isEnabled: Boolean = false,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
)
