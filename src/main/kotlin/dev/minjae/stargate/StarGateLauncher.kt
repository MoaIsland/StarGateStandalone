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
import java.util.concurrent.atomic.AtomicBoolean

typealias SessionFunc = (ServerSession) -> Unit

object StarGateLauncher {
    val authenticatedHandlers: MutableList<SessionFunc> = mutableListOf()
    val disconnectedHandlers: MutableList<SessionFunc> = mutableListOf()

    @JvmStatic
    fun main(args: Array<String>) {
        val mapper = YAMLMapper()
            .registerKotlinModule()
            .registerModule(BlackbirdModule())
        val config: StarGateConfig = File("config.yml").apply {
            if (!exists()) {
                createNewFile()
                writeBytes({}.javaClass.getResourceAsStream("/config.yml")!!.readAllBytes())
            }
        }.inputStream().bufferedReader().use(mapper::readValue)
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
        val shutdown = AtomicBoolean(false)
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown.set(true)
            logger.info("Shutting down...")
            pluginManager.disablePlugins()
            thread.shutdown()
            logger.info("Shutdown complete.")
        })
        while (!shutdown.get()) {
            // NOOP
        }
    }
}