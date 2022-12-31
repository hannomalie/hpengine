package de.hanno.hpengine.artemis

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.lifecycle.Updatable
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File


class KotlinComponent : Component() {
    lateinit var codeSource: CodeSource
}
@All(KotlinComponent::class)
class KotlinComponentSystem(
    private val fileMonitor: FileMonitor,
) : BaseEntitySystem() {
    lateinit var kotlinComponentMapper: ComponentMapper<KotlinComponent>

    // TODO: Abstract over extension points maybe?
    private val updatableInstances = mutableMapOf<Int, Updatable>()

    private val compiler = K2JVMCompiler()
    private val outputFolder = File(System.getProperty("java.io.tmpdir"))
        .resolve("hp_kotlin_${System.currentTimeMillis()}")
        .apply {
            mkdirs()
        }

    override fun inserted(entityId: Int) {
        val kotlinComponent = kotlinComponentMapper[entityId]
        val codeSource = kotlinComponent.codeSource
        val className = codeSource.name

        if (codeSource is FileBasedCodeSource) {
            fileMonitor.addOnFileChangeListener(codeSource.file) {
                codeSource.reload()
                compileAndCreateInstance(className, entityId, codeSource)
            }
        }

        codeSource.load()

        compileAndCreateInstance(className, entityId, codeSource)

    }

    private fun compileAndCreateInstance(
        className: String,
        entityId: Int,
        codeSource: CodeSource
    ) {
        val tempKtFile = outputFolder.resolve("$className.kt").apply {
            createNewFile()
            writeText(codeSource.source)
        }
        tempKtFile.compile()
        val loadedClass = CustomClassLoader(outputFolder).loadClass(className)

        val instance = loadedClass.constructors.first().newInstance()
        if (instance is Updatable) {
            updatableInstances[entityId] = instance
        }
    }

    private fun File.compile() {
        compiler.run {
            val args = K2JVMCompilerArguments().apply {
                freeArgs = listOf(absolutePath)
                destination = outputFolder.absolutePath
                classpath = System.getProperty("java.class.path")
                    .split(System.getProperty("path.separator"))
                    .filter {
                        File(it).exists() && File(it).canRead()
                    }.joinToString(System.getProperty("path.separator"))
                noStdlib = true
                noReflect = true
                contextReceivers = true
                reportPerf = true
            }
            execImpl(
                PrintingMessageCollector(
                    System.out,
                    MessageRenderer.WITHOUT_PATHS, true
                ), Services.EMPTY, args
            )
        }
    }

    override fun removed(entityId: Int) {
        updatableInstances.remove(entityId)
    }

    override fun processSystem() {
        updatableInstances.values.forEach { it.update(world.delta) }
    }
}

// baeldung.com/java-classloaders
class CustomClassLoader(val baseDir: File) : ClassLoader() {

    public override fun findClass(name: String): Class<*> {
        val classBytes = loadClassFromFile(name)
        return defineClass(name, classBytes, 0, classBytes.size)
    }

    private fun loadClassFromFile(fileName: String): ByteArray =
        baseDir.resolve(fileName.replace('.', File.separatorChar) + ".class").readBytes()
}