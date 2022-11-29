package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.Component
import de.hanno.hpengine.artemis.CameraComponent
import de.hanno.hpengine.artemis.GiVolumeComponent
import de.hanno.hpengine.artemis.OceanWaterComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.model.Mesh
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.artemis.ModelComponent as ModelComponentArtemis

sealed class Selection {
    object None: Selection()
}

data class MaterialSelection(val material: Material): Selection() {
    override fun toString() = material.name
}
sealed class EntitySelection(val entity: Int, val components: List<Component>): Selection() {
    override fun toString(): String = entity.toString()
}
data class SimpleEntitySelection(val _entity: Int, val _components: List<Component>): EntitySelection(_entity, _components){
    override fun toString(): String = entity.toString()
}
data class NameSelection(private val _entity: Int, val name: String, val _components: List<Component>): EntitySelection(_entity, _components) {
    override fun toString(): String = name
}
data class TransformSelection(private val _entity: Int, val transform: TransformComponent, val _components: List<Component>): EntitySelection(_entity, _components) {
    override fun toString(): String = entity.toString()
}
data class MeshSelection(private val _entity: Int, val mesh: Mesh<*>, val modelComponent: ModelComponentArtemis, val _components: List<Component>): EntitySelection(_entity, _components) {
    override fun toString(): String = mesh.name
}
data class ModelSelection(private val _entity: Int, val modelComponent: ModelComponentArtemis, val model: Model<*>, val _components: List<Component>): EntitySelection(_entity, _components) {
    override fun toString(): String = model.file.name
}
data class ModelComponentSelection(private val _entity: Int, val modelComponent: ModelComponentArtemis, val _components: List<Component>): EntitySelection(_entity, _components) {
    override fun toString(): String = when(val description = modelComponent.modelComponentDescription) {
        is AnimatedModelComponentDescription -> "[" + description.directory.name + "]" + description.file
        is StaticModelComponentDescription -> "[" + description.directory.name + "]" + description.file
    }
}
data class CameraSelection(private val _entity: Int, val cameraComponent: CameraComponent, val _components: List<Component>): EntitySelection(_entity, _components)

data class GiVolumeSelection(val giVolumeComponent: GiVolumeComponent): Selection()
data class OceanWaterSelection(val oceanWater: OceanWaterComponent): Selection()
data class ReflectionProbeSelection(val reflectionProbe: ReflectionProbe): Selection()
