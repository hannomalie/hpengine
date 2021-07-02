package de.hanno.hpengine.engine.scene.api

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager

data class Scene(val name: String, val entities: MutableList<Entity> = mutableListOf())
fun scene(name: String, block: Scene.() -> Unit): Scene = Scene(name).apply(block)

data class Entity(val name: String, val components: MutableList<Component> = mutableListOf()) {
    fun add(component: Component) {
        components.add(component)
    }
}
fun Scene.entity(name: String, block: Entity.() -> Unit) {
    entities.add(Entity(name).apply(block))
}

interface Component
sealed class ModelComponent: Component {
    abstract val directory: Directory
}
data class StaticModelComponent(val file: String, override val directory: Directory): ModelComponent()
data class AnimatedModelComponent(val file: String, override val directory: Directory): ModelComponent()
enum class Directory { Game, Engine }

data class CustomComponent(val update: suspend (de.hanno.hpengine.engine.scene.Scene, Float) -> Unit): Component

fun Scene.convert(engineContext: EngineContext) = de.hanno.hpengine.engine.scene.Scene(name).apply {
    addAll(entities.map {
        de.hanno.hpengine.engine.entity.Entity(it.name).apply {
            it.components.forEach {
                val it = when(it) {
                    is ModelComponent -> {
                        val dir = when(it.directory) {
                            Directory.Game -> engineContext.config.gameDir
                            Directory.Engine -> engineContext.config.engineDir
                        }
                        val model = when(it) {
                            is AnimatedModelComponent -> AnimatedModelLoader().load(it.file, engineContext.textureManager, dir)
                            is StaticModelComponent -> StaticModelLoader().load(it.file, engineContext.textureManager, dir)
                        }
                        de.hanno.hpengine.engine.component.ModelComponent(this, model)
                    }
                    is CustomComponent -> object: de.hanno.hpengine.engine.component.CustomComponent {
                        override val entity = this@apply
                        override suspend fun update(scene: de.hanno.hpengine.engine.scene.Scene, deltaSeconds: Float) = it.update(scene, deltaSeconds)
                    }
                    else -> throw IllegalStateException("Cannot map component definition $it to a runtime type")
                }
                addComponent(it)
            }
        }
    })
}