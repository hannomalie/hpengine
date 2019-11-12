package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import kotlinx.coroutines.CoroutineScope
import java.util.*

open class Instance
    @JvmOverloads constructor(val entity: Entity, transform: Transform<out Transform<*>> = Transform(),
                              var materials: List<SimpleMaterial> = listOf(),
                              val animationController: AnimationController? = null,
                              open val spatial: Spatial = SimpleSpatial())
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

    override val minMax: AABB
        get() = spatial.minMax

    override val minMaxWorld: AABB
        get() = spatial.minMaxWorld
}
