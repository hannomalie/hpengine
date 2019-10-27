package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import java.util.concurrent.atomic.AtomicInteger

interface ComponentMapper<T: Component> {
    val clazz: Class<T>
    val index: Int

    fun getComponent(entity: Entity): T? {
        return entity.getComponent(clazz)
    }

    companion object {
        private val counter = AtomicInteger()
        private val cache = mutableMapOf<Class<*>, ComponentMapper<*>>()

        fun <T: Component> forClass(clazz: Class<T>): ComponentMapper<T> {
            return cache.computeIfAbsent(clazz, { SimpleComponentMapper(clazz, counter.getAndIncrement()) }) as ComponentMapper<T>
        }
    }
}
class SimpleComponentMapper<T: Component>(override val clazz: Class<T>, override val index: Int) : ComponentMapper<T>

