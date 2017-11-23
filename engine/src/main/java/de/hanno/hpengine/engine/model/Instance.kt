package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialFactory
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import java.util.*

open class Instance
    @JvmOverloads constructor(transform: Transform<out Transform<*>> = Transform(),
                              var materials: List<Material> = listOf(),
                              val animationController: AnimationController = AnimationController(0, 0f),
                              open val spatial: Spatial = SimpleSpatial())
    : Transform<Transform<*>>(), LifeCycle, Spatial by spatial {


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

    override fun isInitialized(): Boolean {
        return true
    }

    override fun update(seconds: Float) {
        animationController.update(seconds)
    }

}
