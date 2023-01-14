package de.hanno.hpengine.artemis.model

import com.artemis.Component
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.transform.SimpleSpatial

// TODO: Change lateinit var for constructor parameters and use World.add() instead of World.create()
class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}
class ModelCacheComponent : Component() {
    lateinit var model: Model<*>
    lateinit var allocation: Allocation // TODO: sometimes, this is accessed before initialization, check out why
    lateinit var meshSpatials: List<SimpleSpatial>
}

class MaterialComponent: Component() {
    lateinit var material: Material
}
