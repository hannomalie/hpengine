package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileMonitor
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener
import de.hanno.hpengine.util.ressources.Reloadable
import de.swirtz.ktsrunner.objectloader.KtsObjectLoader
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.util.HashMap

class KotlinComponent(private val kotlinCodeSource: CodeSource) : BaseComponent(), ScriptComponent, Reloadable {
    init {
        require(kotlinCodeSource.isFileBased) { throw IllegalArgumentException("Kotlin code sources have to be file based currently!") }
    }
    private var observerKotlinFile: FileAlterationObserver? = null
    private var reloadOnFileChangeListener: ReloadOnFileChangeListener<KotlinComponent>? = null


    private val map = HashMap<Any, Any>()
    var compiledClass: Class<*>? = null
        private set
    private var isLifeCycle: Boolean = false
    private var isEngineConsumer: Boolean = false
    var instance: Any? = null
        private set

    val sourceCode: String
        get() = kotlinCodeSource.source

    constructor(sourceCode: String) : this(CodeSource(sourceCode)) {}

    override fun getIdentifier(): String {
        return "KotlinComponent"
    }

    override fun init(engine: EngineContext<*>) {
        observerKotlinFile = FileAlterationObserver(if (kotlinCodeSource.isFileBased) kotlinCodeSource.file.parent else directory)
        addFileListeners()
        initWrappingComponent()
        super.init(engine)
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

    override fun put(key: Any, value: Any): Any? {
        return map.put(key, value)
    }

    private fun initWrappingComponent() {
        try {
            objectLoader.engine.eval(kotlinCodeSource.source)
            instance = objectLoader.engine.eval("${kotlinCodeSource.filename}()")
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

    private fun addFileListeners() {

        clearListeners()

        reloadOnFileChangeListener = object : ReloadOnFileChangeListener<KotlinComponent>(this) {
            override fun shouldReload(changedFile: File): Boolean {
                val fileName = FilenameUtils.getBaseName(changedFile.absolutePath)
                return kotlinCodeSource.isFileBased && kotlinCodeSource.filename.startsWith(fileName)
            }
        }

        observerKotlinFile!!.addListener(reloadOnFileChangeListener)
        FileMonitor.getInstance().add(observerKotlinFile)
    }

    private fun clearListeners() {
        if (observerKotlinFile != null) {
            observerKotlinFile!!.removeListener(reloadOnFileChangeListener)
        }
    }

    override fun load() {
        kotlinCodeSource.load()
        initWrappingComponent()
    }

    override fun unload() {

    }

    companion object {

        val WORKING_DIR = Config.getInstance().directoryManager.gameDir.java.path

        init {
            val workingDir = File(WORKING_DIR)
            try {
                if (!workingDir.exists()) {
                    Files.createDirectory(workingDir.toPath())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        internal val objectLoader = KtsObjectLoader()

        private val directory: String
            get() = Config.getInstance().directoryManager.gameDir.scripts.path
    }
}
