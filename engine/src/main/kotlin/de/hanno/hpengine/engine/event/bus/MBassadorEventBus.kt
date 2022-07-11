package de.hanno.hpengine.engine.event.bus

import net.engio.mbassy.bus.MBassador
import net.engio.mbassy.bus.config.IBusConfiguration
import net.engio.mbassy.bus.config.BusConfiguration
import net.engio.mbassy.bus.error.IPublicationErrorHandler
import net.engio.mbassy.bus.error.PublicationError
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import net.engio.mbassy.bus.config.Feature
import java.lang.IllegalStateException
import java.util.logging.Logger

class MBassadorEventBus @JvmOverloads constructor(defaultAsync: Boolean = true) : EventBus {
    private val eventBus: MBassador<Any>
    private val defaultAsync: Boolean
    override fun <EVENT_TYPE : Any> post(event: EVENT_TYPE) {
        if (defaultAsync) {
            eventBus.post(event).asynchronously()
        } else {
            eventBus.post(event).now()
        }
    }

    override fun register(`object`: Any?) {
        eventBus.subscribe(`object`)
    }

    override fun unregister(`object`: Any?) {
//        eventBus.unsubscribe(object);
//        No need to unsubscribe since WeakReferences are used
    }

    companion object {
        private val LOGGER = Logger.getLogger(EventBus::class.java.name)
    }

    init {
        val config: IBusConfiguration = BusConfiguration()
            .addFeature(Feature.SyncPubSub.Default())
            .addFeature(Feature.AsynchronousHandlerInvocation.Default())
            .addFeature(Feature.AsynchronousMessageDispatch.Default())
            .addPublicationErrorHandler { error: PublicationError ->
                LOGGER.severe(error.message)
                LOGGER.severe(error.cause.toString())
                LOGGER.severe(error.publishedMessage.toString())
                error.cause.printStackTrace()
                throw IllegalStateException("Eventbus error")
            }
        eventBus = MBassador<Any>(config)
        this.defaultAsync = defaultAsync
    }
}