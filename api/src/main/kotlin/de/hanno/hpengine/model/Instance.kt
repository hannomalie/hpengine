package de.hanno.hpengine.model

import de.hanno.hpengine.Parentable
import de.hanno.hpengine.Transform
import de.hanno.hpengine.lifecycle.Updatable
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.AABB

class Instance(
    val transform: Transform = Transform(),
    var materials: List<Material> = listOf(),
    val animationController: AnimationController? = null,
    val boundingVolume: AABB = AABB(),
) : Parentable<Instance>, Updatable {

    override val children = ArrayList<Instance>()
    override var parent: Instance? = null
        set(value) {
            transform.parent = value?.parent?.transform
            field = value
        }

    override fun addChild(child: Instance) {
        transform.addChild(child.transform)
        if (!hasChildInHierarchy(child)) {
            children.add(child)
        }
    }

    override fun removeChild(child: Instance) {
        transform.removeChild(child.transform)
        children.remove(child)
    }

    override fun update(deltaSeconds: Float) {
        animationController?.update(deltaSeconds)
    }
}
