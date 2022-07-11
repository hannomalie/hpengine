package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.Component
import de.hanno.hpengine.engine.component.artemis.CameraComponent
import de.hanno.hpengine.engine.component.artemis.GiVolumeComponent
import de.hanno.hpengine.engine.component.artemis.OceanWaterComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.component.artemis.ModelComponent as ModelComponentArtemis

sealed class Selection {
    object None: Selection()
}

data class MaterialSelection(val material: Material): Selection() {
    override fun toString() = material.name
}
sealed class EntitySelection(val entity: Int): Selection() {
    override fun toString(): String = entity.toString()
}
data class SimpleEntitySelection(val _entity: Int, val components: List<Component>): EntitySelection(_entity){
    override fun toString(): String = entity.toString()
}
data class NameSelection(private val _entity: Int, val name: String): EntitySelection(_entity) {
    override fun toString(): String = name
}
data class TransformSelection(private val _entity: Int, val transform: TransformComponent): EntitySelection(_entity) {
    override fun toString(): String = entity.toString()
}
data class MeshSelection(private val _entity: Int, val mesh: Mesh<*>, val modelComponent: ModelComponentArtemis): EntitySelection(_entity) {
    override fun toString(): String = mesh.name
}
data class ModelSelection(private val _entity: Int, val modelComponent: ModelComponentArtemis, val model: Model<*>): EntitySelection(_entity) {
    override fun toString(): String = model.file.name
}
data class ModelComponentSelection(private val _entity: Int, val modelComponent: ModelComponentArtemis): EntitySelection(_entity) {
    override fun toString(): String = when(val description = modelComponent.modelComponentDescription) {
        is AnimatedModelComponentDescription -> "[" + description.directory.name + "]" + description.file
        is StaticModelComponentDescription -> "[" + description.directory.name + "]" + description.file
    }
}
data class CameraSelection(private val _entity: Int, val cameraComponent: CameraComponent): EntitySelection(_entity)

data class GiVolumeSelection(val giVolumeComponent: GiVolumeComponent): Selection()
data class OceanWaterSelection(val oceanWater: OceanWaterComponent): Selection()
data class ReflectionProbeSelection(val reflectionProbe: ReflectionProbe): Selection()
