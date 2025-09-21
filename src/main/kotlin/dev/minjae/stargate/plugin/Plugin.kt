package dev.minjae.stargate.plugin

import alemiz.stargate.server.StarGateServer

interface Plugin {
    fun onEnable(server: StarGateServer)
    fun onDisable()
}