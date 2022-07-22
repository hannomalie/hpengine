package de.hanno.hpengine.model

import de.hanno.hpengine.lifecycle.Updatable
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.Spatial
import de.hanno.hpengine.transform.Transform
import de.hanno.hpengine.transform.TransformSpatial
import de.hanno.hpengine.Parentable

class Instance
    @JvmOverloads constructor(val transform: Transform = Transform(),
                              var materials: List<Material> = listOf(),
                              val animationController: AnimationController? = null,
                              val _boundingVolume: AABB = AABB(),
                              val spatial: TransformSpatial = TransformSpatial(
                                  transform,
                                  _boundingVolume
                              )
    )
    : Parentable<Instance>, Updatable, Spatial by spatial {

    override val children = ArrayList<Instance>()
    override var parent: Instance? = null
        set(value) {
            transform.parent = value?.parent?.transform
            field = value
        }

    override fun addChild(child: Instance) {
        transform.addChild(child.transform)
        if(!hasChildInHierarchy(child)) {
            children.add(child)
        }
    }

    override fun removeChild(child: Instance) {
        transform.removeChild(child.transform)
        children.remove(child)
    }

    override fun update(deltaSeconds: Float) {
        animationController?.update(deltaSeconds)
        spatial.update(deltaSeconds)
    }

}
