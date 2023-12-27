package de.hanno.hpengine.model

import com.artemis.Component
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.ModelComponentDescription

class ModelComponent : Component() {
    lateinit var modelComponentDescription: ModelComponentDescription
}
class PreventDefaultRendering: Component()
class ModelCacheComponent() : Component() {
    lateinit var model: Model<*>
    lateinit var allocation: Allocation

    constructor(
        model: Model<*>,
        allocation: Allocation,
    ) : this() {
        this.model = model
        this.allocation = allocation
    }
}
class AnimationControllerComponent(): Component() {
    lateinit var animationController: AnimationController

    constructor(animationController: AnimationController): this() {
        this.animationController = animationController
    }
}

class MaterialComponent: Component() {
    lateinit var material: Material
}
