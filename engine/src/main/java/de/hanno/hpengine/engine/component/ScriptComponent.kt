package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.Reloadable
import java.io.File

interface ScriptComponent: Component, Reloadable {
    val codeSource: CodeSource

    operator fun get(key: Any): Any
    fun put(key: Any, value: Any): Any
}

sealed class ScriptComponentFileLoader<out T: ScriptComponent> {
    abstract fun load(engine: Engine<*>, codeFile: File): T

    companion object {
        fun getLoaderForFileExtension(extension: String): ScriptComponentFileLoader<ScriptComponent> {
            return when(extension) {
                "java" -> JavaComponentLoader
                "kt", "kts" -> KotlinCompiledComponentLoader
                else -> throw IllegalArgumentException("Unregistered file extension: $extension")
            }
        }
    }
}

object KotlinComponentLoader: ScriptComponentFileLoader<KotlinComponent>() {
    override fun load(engine: Engine<*>, codeFile: File): KotlinComponent {
        val script = KotlinComponent(CodeSource(codeFile))
        script.init(engine)
        script.initWithEngine(engine)
        return script
    }
}
object KotlinCompiledComponentLoader: ScriptComponentFileLoader<KotlinCompiledComponent>() {
    override fun load(engine: Engine<*>, codeFile: File): KotlinCompiledComponent {
        val script = KotlinCompiledComponent(CodeSource(codeFile))
        script.init(engine)
        script.initWithEngine(engine)
        return script
    }
}
object JavaComponentLoader: ScriptComponentFileLoader<JavaComponent>() {
    override fun load(engine: Engine<*>, codeFile: File): JavaComponent {
        val script = JavaComponent(CodeSource(codeFile), engine.config.directories.gameDir)
        script.init(engine)
        script.initWithEngine(engine)
        return script
    }
}