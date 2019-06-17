package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.util.ressources.CodeSource
import de.swirtz.ktsrunner.objectloader.KtsObjectLoader
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URLClassLoader
import java.util.HashMap

class KotlinCompiledComponent(override val codeSource: CodeSource) : BaseComponent(), ScriptComponent {
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
            val output = codeSource.file.parentFile

            kotlinCompiler.run {
                val args = K2JVMCompilerArguments().apply {
                    freeArgs = listOf(codeSource.file.absolutePath)
                    loadBuiltInsFromDependencies = true
                    destination = output.absolutePath
                    classpath = System.getProperty("java.class.path")
                            .split(System.getProperty("path.separator"))
                            .filter {
                                File(it).exists() && File(it).canRead()
                            }.joinToString(":")
                    noStdlib = true
                    noReflect = true
                    skipRuntimeVersionCheck = true
                    reportPerf = true
                }
                execImpl(
                        PrintingMessageCollector(
                                System.out,
                                MessageRenderer.WITHOUT_PATHS, true),
                        Services.EMPTY,
                        args)
            }
            val resolvedClassFile = output.resolve(codeSource.filename + ".class")
            if(!resolvedClassFile.exists()) throw IllegalStateException("Compiled class file doesn't exist: $resolvedClassFile")

            val classLoader = URLClassLoader(arrayOf(resolvedClassFile.parentFile.toURI().toURL()))
            compiledClass = classLoader.loadClass(codeSource.filename.replace(".kt", "")).apply {
                instance = newInstance()
            }

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

        private val kotlinCompiler = K2JVMCompiler()
    }
}
