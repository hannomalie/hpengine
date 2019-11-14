package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.util.ressources.CodeSource
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URLClassLoader
import java.util.HashMap
import javax.inject.Inject

data class KotlinCompiledComponent(val engine: Engine<*>, override val codeSource: CodeSource, override val entity: Entity) : BaseComponent(entity), ScriptComponent {
    init {
        require(codeSource.isFileBased) { throw IllegalArgumentException("Kotlin code sources have to be file based currently!") }
        initWrappingComponent()
    }

    private val map = HashMap<Any, Any>()
    var compiledClass: Class<*>? = null
        private set
    private var isUpdatable: Boolean = false
    private var isEngineConsumer: Boolean = false
    var instance: Any? = null
        private set

    val sourceCode: String
        get() = codeSource.source

    override fun CoroutineScope.update(deltaSeconds: Float) {
        if (isUpdatable) {
            with(instance as Updatable) {
                update(deltaSeconds)
            }
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
//                    loadBuiltInsFromDependencies = true
                    jvmDefault = "enable"
                    jvmTarget = "1.8"
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
            fun Array<Annotation>.containsInject(): Boolean = map { it.annotationClass.java }.contains(Inject::class.java)
            compiledClass = classLoader.loadClass(codeSource.filename.replace(".kt", "")).apply {
                val firstConstructor = declaredConstructors.first()
                instance = if(firstConstructor.parameters.isEmpty()) {
                    newInstance()
                } else if(firstConstructor.annotations.containsInject()) {

                    val resolvedParams: List<Any> = firstConstructor.parameters.map {
                        when {
                            it.type.isAssignableFrom(Engine::class.java) -> { engine }
                            it.type.isAssignableFrom(Entity::class.java) -> {
                                entity
                            }
                            else -> {
                                throw IllegalStateException("Cannot inject parameter $it in code file ${codeSource.file}")
                            }
                        }
                    }
                    firstConstructor.newInstance(*resolvedParams.toTypedArray())
                } else {
                    throw IllegalStateException("Non empty constructor without @Inject found in code file ${codeSource.file}. Not a good practice.")
                }
            }

//            TODO: Find out where this is used and replace with construtor injection
//            try {
//                val entityField = instance!!.javaClass.getDeclaredField("entity")
//                entityField.set(instance, getEntity())
//            } catch (e: Exception) {
//
//            }

            isUpdatable = instance is Updatable
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
        private val kotlinCompiler = K2JVMCompiler()
    }
}
