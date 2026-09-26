package dev.minjae.stargate

import alemiz.stargate.server.ServerSession
import alemiz.stargate.server.StarGateServerListener
import alemiz.stargate.utils.StarGateLogger
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class StarGateServerListener(
    private val blockSameNames: Boolean,
    private val logger: StarGateLogger
) : StarGateServerListener() {
    // Client name -> the authenticated session that holds it. Only filled while blockSameNames is on.
    private val owners = ConcurrentHashMap<String, ServerSession>()

    // Sessions refused because their name was taken. Plugins never saw them authenticate,
    // so they must not see them disconnect either: a plugin that keys its state by name would
    // otherwise drop the entry of the session that still owns the name.
    private val rejected: MutableSet<ServerSession> = ConcurrentHashMap.newKeySet()

    override fun onSessionAuthenticated(session: ServerSession) {
        if (blockSameNames && !claimName(session)) {
            return
        }
        StarGateLauncher.authenticatedHandlers.forEach { it.invoke(session) }
    }

    /**
     * The first authenticated session keeps the name; a later one with the same name is refused.
     * Keeping the first one matters: the newcomer is usually a copied or restored server directory
     * that nobody meant to run under this name, while the original is serving players.
     */
    private fun claimName(session: ServerSession): Boolean {
        val name = session.sessionName
        // compute() is atomic per key, so two clients authenticating at the same moment cannot both win.
        // A closed owner gives the name up even before its onSessionDisconnected has run (that runs on
        // the owner's own event loop), so a restarted client is not refused by its own dead session.
        val owner = owners.compute(name) { _, current ->
            if (current == null || current === session || current.isClosed) session else current
        }!!
        if (owner === session) {
            return true
        }
        rejected.add(session)
        // SessionHandshakeHandler has already marked the session authenticated and queued ServerHandshake.
        // Clearing the flag keeps it out of anything that filters on isAuthenticated (the addons rebroadcast).
        // Closing it here also drops the queued ServerHandshake: onTick() returns early on a closed session
        // and runs on this same event loop, so the client only ever receives the DisconnectPacket.
        session.isAuthenticated = false
        // The "New client connected! Name: ..." line logged just before this one belongs to the refused
        // connection; SessionHandshakeHandler prints it before handing the session to this listener.
        logger.warn("Rejected StarGate client $name from ${session.address}: the name is already connected from ${owner.address} (block-same-names)")
        session.disconnect(NAME_TAKEN_REASON)
        return false
    }

    override fun onSessionCreated(address: InetSocketAddress, session: ServerSession): Boolean {
        return true
    }

    override fun onSessionDisconnected(session: ServerSession) {
        if (rejected.remove(session)) {
            return
        }
        // handshakeData is null for a session that closed before sending its Handshake, and
        // getSessionName() would throw. StarGateServer calls this listener before removing the session
        // from its map, so an exception here would leave the dead session in server.sessions for good.
        val handshake = session.handshakeData
        if (handshake != null) {
            // remove(key, value) only releases the name when this session is the one holding it.
            owners.remove(handshake.clientName, session)
        }
        StarGateLauncher.disconnectedHandlers.forEach { it.invoke(session) }
    }

    companion object {
        // Sent as the DisconnectPacket reason to a refused client. Clients match on the NAME_TAKEN prefix,
        // so keep it when rewording the rest.
        const val NAME_TAKEN_REASON = "NAME_TAKEN: a client with this name is already connected"
    }
}
