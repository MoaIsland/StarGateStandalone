package dev.minjae.stargate.plugin

import alemiz.stargate.server.StarGateServer
import alemiz.stargate.utils.StarGateLogger
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

class PluginManager(
    private val logger: StarGateLogger,
    private val server: StarGateServer
) {
    private val plugins = mutableListOf<Pair<Plugin, PluginInfo>>()
    private val pluginsDirectory = File("plugins")

    init {
        if (!pluginsDirectory.exists()) {
            pluginsDirectory.mkdirs()
        }
    }

    fun loadPlugins() {
        logger.info("Loading plugins from ${pluginsDirectory.absolutePath}")

        val jarFiles = pluginsDirectory.listFiles { file ->
            file.isFile && file.extension == "jar"
        } ?: return

        for (jarFile in jarFiles) {
            try {
                loadPlugin(jarFile)
            } catch (e: Exception) {
                logger.error("Failed to load plugin from ${jarFile.name}: ${e.message}")
            }
        }

        logger.info("Loaded ${plugins.size} plugin(s)")
    }

    private fun loadPlugin(jarFile: File) {
        val jar = JarFile(jarFile)
        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)

        val pluginYml = jar.getEntry("plugin.yml")
        if (pluginYml == null) {
            logger.warn("No plugin.yml found in ${jarFile.name}, skipping")
            return
        }

        val pluginData = jar.getInputStream(pluginYml).bufferedReader().use { reader ->
            reader.readLines()
                .filter { it.isNotBlank() && !it.trim().startsWith("#") }
                .associate { line ->
                    val parts = line.split(":", ignoreCase = false, limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim() to parts[1].trim()
                    } else {
                        "" to ""
                    }
                }
        }

        val mainClass = pluginData["main"] ?: throw IllegalArgumentException("No main class specified in plugin.yml")

        val pluginInfo = PluginInfo(
            name = pluginData["name"] ?: throw IllegalArgumentException("No name specified in plugin.yml"),
            version = pluginData["version"] ?: "1.0.0",
            description = pluginData["description"] ?: "",
            main = mainClass,
            author = pluginData["author"]
        )

        // Pre-load all classes in the plugin JAR to make them available
        val classNames = mutableListOf<String>()
        jar.entries().asSequence().forEach { entry ->
            if (entry.name.endsWith(".class") && !entry.name.contains("$")) {
                val className = entry.name.removeSuffix(".class").replace("/", ".")
                classNames.add(className)
            }
        }

        // Load all classes into the class loader
        classNames.forEach { className ->
            try {
                classLoader.loadClass(className)
                logger.debug("Loaded class: $className")
            } catch (e: Exception) {
                logger.debug("Failed to load class $className: ${e.message}")
            }
        }

        val clazz = classLoader.loadClass(mainClass)
        val pluginInstance = clazz.getDeclaredConstructor().newInstance() as Plugin

        plugins.add(pluginInstance to pluginInfo)
        logger.info("Loaded plugin: ${pluginInfo.name} v${pluginInfo.version} with ${classNames.size} classes")
    }

    fun enablePlugins() {
        logger.info("Enabling plugins...")
        plugins.forEach { (plugin, info) ->
            try {
                plugin.onEnable(server)
                logger.info("Enabled plugin: ${info.name}")
            } catch (e: Exception) {
                logger.error("Failed to enable plugin ${info.name}: ${e.message}")
            }
        }
    }

    fun disablePlugins() {
        logger.info("Disabling plugins...")
        plugins.forEach { (plugin, info) ->
            try {
                plugin.onDisable()
                logger.info("Disabled plugin: ${info.name}")
            } catch (e: Exception) {
                logger.error("Failed to disable plugin ${info.name}: ${e.message}")
            }
        }
    }

    fun getPlugins(): List<Plugin> = plugins.map { it.first }
    fun getPluginInfos(): List<PluginInfo> = plugins.map { it.second }
}