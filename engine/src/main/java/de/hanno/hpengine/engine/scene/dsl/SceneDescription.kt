package de.hanno.hpengine.engine.scene.dsl

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableInputComponent
import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightControllerComponent
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.extension.Extension
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABBData
import org.koin.core.KoinApplication

data class SceneDescription(val name: String, val entities: MutableList<EntityDescription> = mutableListOf())
fun scene(name: String, block: SceneDescription.() -> Unit): SceneDescription = SceneDescription(name).apply(block)

data class EntityDescription(val name: String, val components: MutableList<ComponentDescription> = mutableListOf()) {
    var contributesToGi: Boolean = true

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
    abstract val material: SimpleMaterial?
}
data class StaticModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
    override val material: SimpleMaterial? = null
): ModelComponentDescription()
data class AnimatedModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
    override val material: SimpleMaterial? = null
): ModelComponentDescription()
enum class Directory { Game, Engine }

data class CustomComponentDescription(val update: suspend (Scene, Entity, Float) -> Unit): ComponentDescription
class DirectionalLightControllerComponentDescription: ComponentDescription
class DirectionalLightDescription: ComponentDescription
class MovableInputComponentDescription: ComponentDescription
class CameraDescription: ComponentDescription

fun SceneDescription.convert(application: KoinApplication) = Scene(name).apply {
    val config = application.koin.get<Config>()
    val textureManager = application.koin.get<TextureManager>()

    val extensions = scope.getAll<Extension>().distinct()
    extensions.forEach {
        it.run {
            this@convert.decorate()
        }
    }
    addAll(entities.map {
        Entity(it.name).apply {
            this.contributesToGi = it.contributesToGi
            it.components.forEach { componentDescription ->
                val component = when(componentDescription) {
                    is ModelComponentDescription -> {
                        val dir = when(componentDescription.directory) {
                            Directory.Game -> config.gameDir
                            Directory.Engine -> config.engineDir
                        }
                        val model = when(componentDescription) {
                            is AnimatedModelComponentDescription -> AnimatedModelLoader().load(
                                componentDescription.file,
                                textureManager,
                                dir
                            )
                            is StaticModelComponentDescription -> StaticModelLoader().load(
                                componentDescription.file,
                                textureManager,
                                dir
                            )
                        }
                        ModelComponent(this, model).apply {
                            componentDescription.aabbData?.let { spatial.boundingVolume.localAABB = it }
                            componentDescription.material?.let { this@apply.material = it }
                        }
                    }
                    is CustomComponentDescription -> object: CustomComponent {
                        override val entity = this@apply
                        override suspend fun update(scene: Scene, deltaSeconds: Float) = componentDescription.update(scene, entity, deltaSeconds)
                    }
                    is DirectionalLightControllerComponentDescription -> DirectionalLightControllerComponent(this)
                    is DirectionalLightDescription -> DirectionalLight(this)
                    is MovableInputComponentDescription -> MovableInputComponent(this)
                    is CameraDescription -> Camera(this)
                    else -> throw IllegalStateException("Cannot map component definition $componentDescription to a runtime type")
                }

                addComponent(component)
            }
        }
    })
}