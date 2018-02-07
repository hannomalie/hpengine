package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.Transform
import java.util.*

open class Instance
    @JvmOverloads constructor(val entity: Entity, transform: Transform<out Transform<*>> = Transform(),
                              var materials: List<Material> = listOf(),
                              val animationController: AnimationController = AnimationController(0, 0f),
                              open val spatial: Spatial = object : SimpleSpatial(){
                                  override fun getMinMaxWorld(): AABB {
                                      recalculate(transform)
                                      return super.getMinMaxWorld()
                                  }
                              })
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

    override fun update(engine: Engine, seconds: Float) {
        animationController.update(engine, seconds)
    }

    override fun getMinMax(): AABB {
        return if (animationController.fps > 0)
            entity.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY).model.getMinMax(null, animationController)
        else
            spatial.minMax
    }

    override fun getMinMaxWorld(): AABB {
        return spatial.minMaxWorld
    }
}
