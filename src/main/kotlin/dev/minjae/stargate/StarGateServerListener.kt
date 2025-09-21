package dev.minjae.stargate

import alemiz.stargate.server.ServerSession
import alemiz.stargate.server.StarGateServerListener
import java.net.InetSocketAddress

class StarGateServerListener : StarGateServerListener() {
    override fun onSessionAuthenticated(session: ServerSession) {
        StarGateLauncher.authenticatedHandlers.forEach { it.invoke(session) }
    }

    override fun onSessionCreated(address: InetSocketAddress, session: ServerSession): Boolean {
        return true
    }

    override fun onSessionDisconnected(session: ServerSession) {
        StarGateLauncher.disconnectedHandlers.forEach { it.invoke(session) }
    }
}