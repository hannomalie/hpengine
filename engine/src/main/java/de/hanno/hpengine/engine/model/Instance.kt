package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.util.ArrayList

class Instance
    @JvmOverloads constructor(val entity: Entity, transform: Transform<out Transform<*>> = Transform(),
                              var materials: List<Material> = listOf(),
                              val animationController: AnimationController? = null,
                              val spatial: TransformSpatial = TransformSpatial(transform, entity.getComponent(ModelComponent::class.java)?.spatial?.minMaxLocal ?: AABB()))
    : Transform<Transform<*>>(), Updatable, Spatial by spatial {

    private val children = ArrayList<Instance>()


    init {
        set(transform)
    }

    override fun setParent(parent: Transform<*>?) {
        throw IllegalStateException("No parenting for instances!")
    }

    override fun getChildren(): List<Transform<*>> {
        return children
    }

    override fun CoroutineScope.update(deltaSeconds: Float) {
        animationController?.update(deltaSeconds)
        with(spatial) { update(deltaSeconds) }
    }

    val minMax: AABB
        get() = spatial.minMax

}
