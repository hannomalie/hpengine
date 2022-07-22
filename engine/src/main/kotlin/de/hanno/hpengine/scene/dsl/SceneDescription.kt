package de.hanno.hpengine.scene.dsl

import de.hanno.hpengine.transform.AABBData
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
}
data class StaticModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
): ModelComponentDescription()
data class AnimatedModelComponentDescription(
    val file: String,
    override val directory: Directory,
    override val aabbData: AABBData? = null,
): ModelComponentDescription()
enum class Directory { Game, Engine }

data class CustomComponentDescription(val update: (Float) -> Unit): ComponentDescription
class OceanWaterDescription: ComponentDescription
