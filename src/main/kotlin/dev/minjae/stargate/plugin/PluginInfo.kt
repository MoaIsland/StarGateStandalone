package dev.minjae.stargate.plugin

data class PluginInfo(
    val name: String,
    val version: String,
    val description: String,
    val main: String,
    val author: String? = null
)