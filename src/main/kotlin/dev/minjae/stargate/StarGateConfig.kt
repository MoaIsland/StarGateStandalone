package dev.minjae.stargate

import com.fasterxml.jackson.annotation.JsonProperty

data class StarGateConfig(
    val bind: BindConfig,
    val auth: AuthConfig,
    val debug: Boolean
) {
    data class BindConfig(
        val address: String,
        val port: Int
    )
    data class AuthConfig(
        val password: String,
        @get:JsonProperty("block-same-names")
        val blockSameNames: Boolean
    )
}