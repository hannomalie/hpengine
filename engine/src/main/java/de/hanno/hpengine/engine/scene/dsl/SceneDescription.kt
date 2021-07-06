package de.hanno.hpengine.engine.scene.dsl

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.transform.AABBData

data class SceneDescription(val name: String, val entities: MutableList<EntityDescription> = mutableListOf())
fun scene(name: String, block: SceneDescription.() -> Unit): SceneDescription = SceneDescription(name).apply(block)

data class EntityDescription(val name: String, val components: MutableList<ComponentDescription> = mutableListOf()) {
    fun add(component: ComponentDescription) {
        components.add(component)
    }
}
fun SceneDescription.entity(name: String, block: EntityDescription.() -> Unit) {
    entities.add(EntityDescription(name).apply(block))
}

interface ComponentDescription
sealed class ModelComponentDescription: ComponentDescription {
    abstract val directory: Directory
    abstract val aabbData: AABBData?
}
data class StaticModelComponentDescription(val file: String, override val directory: Directory, override val aabbData: AABBData? = null): ModelComponentDescription()
data class AnimatedModelComponentDescription(val file: String, override val directory: Directory, override val aabbData: AABBData? = null): ModelComponentDescription()
enum class Directory { Game, Engine }

data class CustomComponentDescription(val update: suspend (de.hanno.hpengine.engine.scene.Scene, Float) -> Unit): ComponentDescription

fun SceneDescription.convert(engineContext: EngineContext) = de.hanno.hpengine.engine.scene.Scene(name).apply {
    addAll(entities.map {
        de.hanno.hpengine.engine.entity.Entity(it.name).apply {
            it.components.forEach { componentDescription ->
                val component = when(componentDescription) {
                    is ModelComponentDescription -> {
                        val dir = when(componentDescription.directory) {
                            Directory.Game -> engineContext.config.gameDir
                            Directory.Engine -> engineContext.config.engineDir
                        }
                        val model = when(componentDescription) {
                            is AnimatedModelComponentDescription -> AnimatedModelLoader().load(componentDescription.file, engineContext.textureManager, dir)
                            is StaticModelComponentDescription -> StaticModelLoader().load(componentDescription.file, engineContext.textureManager, dir)
                        }
                        de.hanno.hpengine.engine.component.ModelComponent(this, model).apply {
                            componentDescription.aabbData?.let { spatial.boundingVolume.localAABB = it }
                        }
                    }
                    is CustomComponentDescription -> object: de.hanno.hpengine.engine.component.CustomComponent {
                        override val entity = this@apply
                        override suspend fun update(scene: de.hanno.hpengine.engine.scene.Scene, deltaSeconds: Float) = componentDescription.update(scene, deltaSeconds)
                    }
                    else -> throw IllegalStateException("Cannot map component definition $componentDescription to a runtime type")
                }
                addComponent(component)
            }
        }
    })
}