package de.hanno.hpengine.bus

interface EventBus {
    fun <EVENT_TYPE : Any> post(event: EVENT_TYPE)
    fun register(`object`: Any?)
    fun unregister(`object`: Any?)
}