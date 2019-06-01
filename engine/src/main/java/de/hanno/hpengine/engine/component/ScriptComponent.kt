package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.util.ressources.CodeSource
import java.io.File
import java.lang.IllegalArgumentException

interface ScriptComponent {
    fun reload()

    operator fun get(key: Any): Any
    fun put(key: Any, value: Any): Any
}

sealed class ScriptComponentFileLoader {
    abstract fun load(engine: Engine<*>, codeFile: File)

    companion object {
        fun getLoaderForFileExtension(extension: String): ScriptComponentFileLoader {
            return when(extension) {
                "java" -> JavaComponentLoader
                "kt", "kts" -> KotlinComponentLoader
                else -> throw IllegalArgumentException("Unregistered file extension: $extension")
            }
        }
    }
}

object KotlinComponentLoader: ScriptComponentFileLoader() {
    override fun load(engine: Engine<*>, codeFile: File) {
        val initScript = KotlinComponent(CodeSource(codeFile))
        initScript.init(engine)
        initScript.initWithEngine(engine)
    }
}
object JavaComponentLoader: ScriptComponentFileLoader() {
    override fun load(engine: Engine<*>, codeFile: File) {
        val initScript = JavaComponent(CodeSource(codeFile))
        initScript.init(engine)
        initScript.initWithEngine(engine)
    }
}