package dev.minjae.stargate

import alemiz.stargate.utils.StarGateLogger
import org.slf4j.LoggerFactory

class LogbackLoggerAdapter : StarGateLogger {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun debug(p0: String?) {
        logger.debug(p0)
    }

    override fun info(p0: String?) {
        logger.info(p0)
    }

    override fun warn(p0: String?) {
        logger.warn(p0)
    }

    override fun error(p0: String?) {
        logger.error(p0)
    }

    override fun error(p0: String?, p1: Throwable?) {
        logger.error(p0, p1)
    }

    override fun logException(p0: Throwable?) {
        logger.error("Exception occurred", p0)
    }
}