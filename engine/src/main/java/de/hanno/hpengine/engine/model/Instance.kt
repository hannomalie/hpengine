package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
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
                              val spatial: TransformSpatial = TransformSpatial(transform, entity.getComponent(ModelComponent::class.java)?.spatial?.xxx ?: AABB()))
    : Parentable<Instance>, Updatable, Spatial by spatial {

    override val children = ArrayList<Instance>()
    override var parent: Instance? = null

    override fun addChild(child: Instance): Instance {
        transform.addChild(child.transform)
        return super.addChild(child)
    }

    override fun removeParent() {
        transform.removeParent()
        super.removeParent()
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        animationController?.update(deltaSeconds)
        with(spatial) { update(deltaSeconds) }
    }

}
