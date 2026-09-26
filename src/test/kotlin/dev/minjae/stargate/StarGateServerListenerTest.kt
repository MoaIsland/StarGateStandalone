package dev.minjae.stargate

import alemiz.stargate.protocol.DisconnectPacket
import alemiz.stargate.protocol.HandshakePacket
import alemiz.stargate.protocol.ServerHandshakePacket
import alemiz.stargate.protocol.types.HandshakeData
import alemiz.stargate.server.ServerSession
import alemiz.stargate.server.StarGateServer
import alemiz.stargate.server.pipeline.SessionChannelHandler
import alemiz.stargate.server.pipeline.SessionHandshakeHandler
import alemiz.stargate.utils.ServerLoader
import alemiz.stargate.utils.StarGateLogger
import io.netty.channel.embedded.EmbeddedChannel
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarGateServerListenerTest {
    private val warnings = mutableListOf<String>()
    private val logger = RecordingLogger(warnings)

    private val authenticated = mutableListOf<ServerSession>()
    private val disconnected = mutableListOf<ServerSession>()
    private val onAuthenticated: SessionFunc = { authenticated.add(it) }
    private val onDisconnected: SessionFunc = { disconnected.add(it) }
    private var nextPort = 40000

    @BeforeTest
    fun registerHandlers() {
        StarGateLauncher.authenticatedHandlers.add(onAuthenticated)
        StarGateLauncher.disconnectedHandlers.add(onDisconnected)
    }

    @AfterTest
    fun unregisterHandlers() {
        StarGateLauncher.authenticatedHandlers.remove(onAuthenticated)
        StarGateLauncher.disconnectedHandlers.remove(onDisconnected)
    }

    /** Builds the same pipeline StarGateServerInitializer does, minus the byte codec. */
    private fun connect(): Pair<EmbeddedChannel, ServerSession> {
        val channel = EmbeddedChannel()
        val session = ServerSession(InetSocketAddress("127.0.0.1", nextPort++), channel, server)
        channel.pipeline().addLast(SessionHandshakeHandler.NAME, SessionHandshakeHandler(session))
        channel.pipeline().addLast(SessionChannelHandler.NAME, SessionChannelHandler(session))
        return channel to session
    }

    private fun handshake(channel: EmbeddedChannel, name: String) {
        val packet = HandshakePacket()
        packet.handshakeData = HandshakeData(name, PASSWORD, HandshakeData.SOFTWARE.PMMP, server.protocolVersion)
        channel.writeInbound(packet)
    }

    /** Lets the session's 50 ms tick flush its queue, then returns everything written to the client. */
    private fun sent(channel: EmbeddedChannel): List<Any> {
        Thread.sleep(80)
        channel.runPendingTasks()
        channel.runScheduledPendingTasks()
        return generateSequence { channel.readOutbound<Any>() }.toList()
    }

    private fun close(channel: EmbeddedChannel, session: ServerSession) {
        session.close()
        channel.runPendingTasks()
    }

    @Test
    fun laterSessionWithTakenNameIsRefusedBeforeServerHandshake() {
        server.serverListener = StarGateServerListener(true, logger)
        val (firstChannel, first) = connect()
        val (secondChannel, second) = connect()

        handshake(firstChannel, "island1")
        handshake(secondChannel, "island1")

        assertTrue(sent(firstChannel).any { it is ServerHandshakePacket })
        val toSecond = sent(secondChannel)
        assertFalse(toSecond.any { it is ServerHandshakePacket })
        val disconnect = toSecond.filterIsInstance<DisconnectPacket>().single()
        assertTrue(disconnect.reason.startsWith("NAME_TAKEN"))
        assertTrue(second.isClosed)
        assertFalse(second.isAuthenticated)
        assertTrue(first.isAuthenticated)
        assertEquals(listOf(first), authenticated)
        // Closing the refused session must not tell plugins that "island1" went away.
        assertTrue(disconnected.isEmpty())
        assertTrue(warnings.single().contains("island1"))
    }

    @Test
    fun refusedSessionClosingDoesNotReleaseTheName() {
        server.serverListener = StarGateServerListener(true, logger)
        val (firstChannel, _) = connect()
        val (secondChannel, second) = connect()
        handshake(firstChannel, "island1")
        handshake(secondChannel, "island1")
        assertTrue(second.isClosed)
        secondChannel.runPendingTasks()

        val (thirdChannel, third) = connect()
        handshake(thirdChannel, "island1")

        assertTrue(third.isClosed)
        assertTrue(sent(thirdChannel).any { it is DisconnectPacket })
    }

    @Test
    fun ownerDisconnectReleasesTheName() {
        server.serverListener = StarGateServerListener(true, logger)
        val (firstChannel, first) = connect()
        handshake(firstChannel, "island1")
        close(firstChannel, first)
        assertEquals(listOf(first), disconnected)

        val (secondChannel, second) = connect()
        handshake(secondChannel, "island1")

        assertFalse(second.isClosed)
        assertTrue(sent(secondChannel).any { it is ServerHandshakePacket })
        assertEquals(listOf(first, second), authenticated)
    }

    @Test
    fun closedOwnerGivesTheNameUpBeforeItsDisconnectCallbackRuns() {
        server.serverListener = StarGateServerListener(true, logger)
        val (firstChannel, first) = connect()
        handshake(firstChannel, "island1")
        // Marked closed, but channelInactive (and so onSessionDisconnected) has not run yet. Closing the
        // EmbeddedChannel would deliver channelInactive at once, so only the session's flag is set here.
        first.closed.set(true)

        val (secondChannel, second) = connect()
        handshake(secondChannel, "island1")
        assertFalse(second.isClosed)

        // The late callback of the old owner must not release the new owner's name.
        firstChannel.close()
        assertEquals(listOf(first), disconnected)
        val (thirdChannel, third) = connect()
        handshake(thirdChannel, "island1")
        assertTrue(third.isClosed)
    }

    @Test
    fun sessionClosedBeforeHandshakeDisconnectsCleanly() {
        server.serverListener = StarGateServerListener(true, logger)
        val (channel, session) = connect()

        // Reading the name of a session without handshake data throws, which would skip the
        // plugin callbacks and leave the session in StarGateServer's map.
        close(channel, session)

        assertEquals(listOf(session), disconnected)
    }

    @Test
    fun sameNamesAreAllowedWhenBlockingIsOff() {
        server.serverListener = StarGateServerListener(false, logger)
        val (firstChannel, first) = connect()
        val (secondChannel, second) = connect()

        handshake(firstChannel, "island1")
        handshake(secondChannel, "island1")

        assertFalse(second.isClosed)
        assertTrue(sent(secondChannel).any { it is ServerHandshakePacket })
        assertEquals(listOf(first, second), authenticated)
    }

    private class RecordingLogger(private val warnings: MutableList<String>) : StarGateLogger {
        override fun debug(message: String?) {}
        override fun info(message: String?) {}
        override fun warn(message: String?) {
            warnings.add(message.orEmpty())
        }
        override fun error(message: String?) {}
        override fun error(message: String?, error: Throwable?) {}
        override fun logException(error: Throwable?) {}
    }

    private companion object {
        const val PASSWORD = "test-password"

        // Never started: the sessions below run on their own EmbeddedChannel event loops, and one
        // instance keeps the tests from opening a fresh pair of Netty event loop groups each.
        val server = StarGateServer(InetSocketAddress("127.0.0.1", 0), PASSWORD, object : ServerLoader {
            override fun getStarGateLogger(): StarGateLogger = RecordingLogger(mutableListOf())
        })
    }
}
