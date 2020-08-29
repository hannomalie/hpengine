package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.Reloadable
import java.io.File

interface ScriptComponent: Component, Reloadable {
    val codeSource: CodeSource

    operator fun get(key: Any): Any
    fun put(key: Any, value: Any): Any
}

sealed class ScriptComponentFileLoader<out T: ScriptComponent> {
    abstract fun load(engine: Engine, codeFile: File, entity: Entity): T

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
    override fun load(engine: Engine, codeFile: File, entity: Entity): KotlinComponent {
        return KotlinComponent(engine, FileBasedCodeSource(codeFile))
    }
}
object KotlinCompiledComponentLoader: ScriptComponentFileLoader<KotlinCompiledComponent>() {
    override fun load(engine: Engine, codeFile: File, entity: Entity): KotlinCompiledComponent {
        return KotlinCompiledComponent(engine, FileBasedCodeSource(codeFile), entity)
    }
}
object JavaComponentLoader: ScriptComponentFileLoader<JavaComponent>() {
    override fun load(engine: Engine, codeFile: File, entity: Entity): JavaComponent {
        return JavaComponent(engine, FileBasedCodeSource(codeFile), engine.engineContext.config.directories.gameDir)
    }
}