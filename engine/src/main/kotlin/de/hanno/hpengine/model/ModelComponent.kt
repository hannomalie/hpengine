package de.hanno.hpengine.model

import com.artemis.Component
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.ModelComponentDescription

// TODO: Change lateinit var for constructor parameters and use World.add() instead of World.create()
class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}
class PreventDefaultRendering: Component()
class ModelCacheComponent : Component() {
    lateinit var model: Model<*>
    lateinit var allocation: Allocation // TODO: sometimes, this is accessed before initialization, check out why
}

class MaterialComponent: Component() {
    lateinit var material: Material
}
