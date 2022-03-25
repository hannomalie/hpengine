package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.Component
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.artemis.OceanWaterComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.component.artemis.ModelComponent as ModelComponentArtemis


sealed class Selection {
    object None: Selection()
}
sealed class EntitySelection(val entity: Entity): Selection() {
    override fun toString(): String = entity.name
}
data class SimpleEntitySelection(val _entity: Entity): EntitySelection(_entity){
    override fun toString(): String = entity.name
}



sealed class SelectionNew {
    object None: SelectionNew()
}

data class SceneSelectionNew(val scene: Scene): SelectionNew()
data class MaterialSelectionNew(val material: Material): SelectionNew() {
    override fun toString() = material.name
}
sealed class EntitySelectionNew(val entity: Int): SelectionNew() {
    override fun toString(): String = entity.toString()
}
data class SimpleEntitySelectionNew(val _entity: Int, val components: List<Component>): EntitySelectionNew(_entity){
    override fun toString(): String = entity.toString()
}
data class NameSelectionNew(private val _entity: Int, val name: String): EntitySelectionNew(_entity) {
    override fun toString(): String = name
}
data class MeshSelectionNew(private val _entity: Int, val mesh: Mesh<*>, val modelComponent: ModelComponent): EntitySelectionNew(_entity) {
    override fun toString(): String = mesh.name
}
data class ModelSelectionNew(private val _entity: Int, val modelComponent: ModelComponent, val model: Model<*>): EntitySelectionNew(_entity) {
    override fun toString(): String = model.file.name
}
data class ModelComponentSelectionNew(private val _entity: Int, val modelComponent: ModelComponentArtemis): EntitySelectionNew(_entity) {
    override fun toString(): String = when(val description = modelComponent.modelComponentDescription) {
        is AnimatedModelComponentDescription -> "[" + description.directory.name + "]" + description.file
        is StaticModelComponentDescription -> "[" + description.directory.name + "]" + description.file
    }
}
data class GiVolumeSelectionNew(val giVolumeComponent: GIVolumeComponent): SelectionNew()
data class OceanWaterSelectionNew(val oceanWater: OceanWaterComponent): SelectionNew()
data class ReflectionProbeSelectionNew(val reflectionProbe: ReflectionProbe): SelectionNew()
