package de.hanno.hpengine.artemis.model

import com.artemis.Component
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.transform.SimpleSpatial

class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}
class ModelCacheComponent : Component() {
    lateinit var model: Model<*>
    lateinit var allocation: Allocation
    lateinit var meshSpatials: List<SimpleSpatial>
}

class MaterialComponent: Component() {
    lateinit var material: Material
}
