package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.OceanWaterExtension
import de.hanno.hpengine.engine.scene.Scene

sealed class Selection {
    object None: Selection()
}

data class SceneSelection(val scene: Scene): Selection()
data class MaterialSelection(val material: Material): Selection() {
    override fun toString() = material.name
}
sealed class EntitySelection(val entity: Entity): Selection() {
    override fun toString(): String = entity.name
}
data class SimpleEntitySelection(val _entity: Entity): EntitySelection(_entity){
    override fun toString(): String = entity.name
}
data class MeshSelection(private val _entity: Entity, val mesh: Mesh<*>): EntitySelection(_entity) {
    override fun toString(): String = mesh.name
}
data class ModelSelection(private val _entity: Entity, val modelComponent: ModelComponent, val model: Model<*>): EntitySelection(_entity) {
    override fun toString(): String = model.file.name
}
data class ModelComponentSelection(private val _entity: Entity, val modelComponent: ModelComponent): EntitySelection(_entity) {
    override fun toString(): String = modelComponent.model.file.name
}
data class PointLightSelection(val light: PointLight): EntitySelection(light.entity)
data class DirectionalLightSelection(val light: DirectionalLight): EntitySelection(light._entity)
data class CameraSelection(val camera: Camera): EntitySelection(camera.entity)
data class GiVolumeSelection(val giVolumeComponent: GIVolumeComponent): Selection()
data class OceanWaterSelection(val oceanWater: OceanWaterExtension.OceanWater): Selection()
data class ReflectionProbeSelection(val reflectionProbe: ReflectionProbe): Selection()
