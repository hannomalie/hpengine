package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.swirtz.ktsrunner.objectloader.KtsObjectLoader
import kotlinx.coroutines.CoroutineScope
import java.util.HashMap

class KotlinComponent(val engine: Engine, override val codeSource: CodeSource) : BaseComponent(Entity()), ScriptComponent {
    init {
        require(codeSource is FileBasedCodeSource) { throw IllegalArgumentException("Kotlin code sources have to be file based currently!") }
        initWrappingComponent()
    }

    private val map = HashMap<Any, Any>()
    var compiledClass: Class<*>? = null
        private set
    private var isLifeCycle: Boolean = false
    private var isEngineConsumer: Boolean = false
    var instance: Any? = null
        private set

    override fun CoroutineScope.update(deltaSeconds: Float) {
        if (isLifeCycle) {
            with(instance as Updatable) {
                update(deltaSeconds)
            }
        }
    }

    override fun reload() {
        unload()
        load()
    }

    override val name: String = toString()

    override fun get(key: Any): Any {
        return map[key] ?: throw IllegalArgumentException("No entry for key $key")
    }

    override fun put(key: Any, value: Any): Any {
        return map.put(key, value)!!
    }

    private fun initWrappingComponent() {
        try {
            codeSource as FileBasedCodeSource
            objectLoader.engine.eval(codeSource.source)
            instance = objectLoader.engine.eval("${codeSource.filename}()")
            compiledClass = instance!!::class.java
            try {
                val entityField = instance!!.javaClass.getDeclaredField("entity")
                entityField.set(instance, entity)
            } catch (e: Exception) {

            }

            isLifeCycle = instance is Updatable
            isEngineConsumer = instance is EngineConsumer

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun load() {
        codeSource.load()
        initWrappingComponent()
    }

    override fun unload() {

    }

    companion object {
        internal val objectLoader = KtsObjectLoader()
    }
}
