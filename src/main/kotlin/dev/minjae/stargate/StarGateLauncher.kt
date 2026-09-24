package dev.minjae.stargate

import alemiz.stargate.server.ServerSession
import alemiz.stargate.server.StarGateServer
import alemiz.stargate.utils.ServerLoader
import alemiz.stargate.utils.StarGateLogger
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.blackbird.BlackbirdModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.minjae.stargate.plugin.PluginManager
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

typealias SessionFunc = (ServerSession) -> Unit

object StarGateLauncher {
    val authenticatedHandlers: MutableList<SessionFunc> = mutableListOf()
    val disconnectedHandlers: MutableList<SessionFunc> = mutableListOf()

    @JvmStatic
    fun main(args: Array<String>) {
        val mapper = YAMLMapper()
            .registerKotlinModule()
            .registerModule(BlackbirdModule())
        val configFile = File("config.yml")
        if (!configFile.exists()) {
            val defaults = StarGateLauncher::class.java.getResourceAsStream("/config.yml")
                ?: error("config.yml is missing from this jar, cannot write the default configuration")
            // createNewFile() is deliberately not used: if reading the defaults fails we do not
            // want to leave behind an empty config.yml that makes every later start fail instead.
            defaults.use { configFile.writeBytes(it.readAllBytes()) }
            println("Created config.yml. Set auth.password before starting the server again.")
            return
        }
        val config: StarGateConfig = configFile.inputStream().bufferedReader().use(mapper::readValue)
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = if (config.debug) Level.DEBUG else Level.INFO
        val logger = LogbackLoggerAdapter()

        val thread = StarGateServer(InetSocketAddress(config.bind.address, config.bind.port), config.auth.password, object : ServerLoader {
            override fun getStarGateLogger(): StarGateLogger {
                return logger
            }
        })

        thread.serverListener = StarGateServerListener()

        val pluginManager = PluginManager(logger, thread)
        pluginManager.loadPlugins()
        pluginManager.enablePlugins()
        thread.isDaemon = true
        thread.start()
        // Parking the main thread on a latch instead of spinning on a flag. The previous busy
        // loop kept one core pinned at 100% for the whole lifetime of the process.
        // Note that joining the server thread does not work: StarGateServer.run() returns as
        // soon as the Netty bind is issued, so the process would exit right after startup.
        val shutdownLatch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down...")
            pluginManager.disablePlugins()
            thread.shutdown()
            logger.info("Shutdown complete.")
            shutdownLatch.countDown()
        })
        shutdownLatch.await()
    }
}