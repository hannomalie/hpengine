package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener
import de.swirtz.ktsrunner.objectloader.KtsObjectLoader
import org.apache.commons.io.monitor.FileAlterationObserver
import java.util.HashMap

class KotlinComponent(override val codeSource: CodeSource) : BaseComponent(), ScriptComponent {
    init {
        require(codeSource.isFileBased) { throw IllegalArgumentException("Kotlin code sources have to be file based currently!") }
    }

    private val map = HashMap<Any, Any>()
    var compiledClass: Class<*>? = null
        private set
    private var isLifeCycle: Boolean = false
    private var isEngineConsumer: Boolean = false
    var instance: Any? = null
        private set

    val sourceCode: String
        get() = codeSource.source

    override fun getIdentifier(): String {
        return "KotlinComponent"
    }

    override fun init(engine: EngineContext<*>) {
        initWrappingComponent()
//        TODO Reimplement me
//        super.init(engine)
        if (isLifeCycle) {
            (instance as LifeCycle).init(engine)
        }
    }

    //    TODO: Make this better
    fun initWithEngine(engine: Engine<*>) {
        if (isEngineConsumer) {
            (instance as EngineConsumer).consume(engine)
        }
    }

    override fun update(seconds: Float) {
        if (isLifeCycle) {
            (instance as LifeCycle).update(seconds)
        }
    }

    override fun reload() {
        unload()
        load()
    }

    override fun getName(): String {
        return this.toString()
    }

    override fun get(key: Any): Any {
        return map[key] ?: throw IllegalArgumentException("No entry for key $key")
    }

    override fun put(key: Any, value: Any): Any {
        return map.put(key, value)!!
    }

    private fun initWrappingComponent() {
        try {
            objectLoader.engine.eval(codeSource.source)
            instance = objectLoader.engine.eval("${codeSource.filename}()")
            compiledClass = instance!!::class.java
            try {
                val entityField = instance!!.javaClass.getDeclaredField("entity")
                entityField.set(instance, getEntity())
            } catch (e: Exception) {

            }

            isLifeCycle = instance is LifeCycle
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