package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import de.hanno.hpengine.util.Parentable
import kotlinx.coroutines.CoroutineScope
import java.util.ArrayList

class Instance
    @JvmOverloads constructor(val entity: Entity,
                              val transform: Transform = Transform(),
                              var materials: List<Material> = listOf(),
                              val animationController: AnimationController? = null,
                              val spatial: TransformSpatial = TransformSpatial(transform, entity.getComponent(ModelComponent::class.java)?.spatial?.boundingVolume ?: AABB()))
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

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        animationController?.update(deltaSeconds)
        with(spatial) { update(scene, deltaSeconds) }
    }

}
