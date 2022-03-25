package de.hanno.hpengine.engine.scene.dsl

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Matrix4f

data class SceneDescription(val name: String, val entities: MutableList<EntityDescription> = mutableListOf())
fun scene(name: String, block: SceneDescription.() -> Unit): SceneDescription = SceneDescription(name).apply(block)

data class EntityDescription(val name: String, val components: MutableList<ComponentDescription> = mutableListOf()) {
    var transform = Matrix4f()
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
    abstract var material: Material?
}
data class StaticModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
    override var material: Material? = null
): ModelComponentDescription()
data class AnimatedModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
    override var material: Material? = null
): ModelComponentDescription()
enum class Directory { Game, Engine }

data class CustomComponentDescription(val update: suspend (Scene, Entity, Float) -> Unit): ComponentDescription
class OceanWaterDescription: ComponentDescription

fun SceneDescription.convert(config: Config, textureManager: TextureManager) = Scene(name).apply {

    addAll(entities.map {
        Entity(it.name).apply {
            this.contributesToGi = it.contributesToGi
        }
    })
}