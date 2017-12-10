package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.LifeCycle
import de.hanno.hpengine.engine.transform.*
import org.joml.Vector3f
import java.util.*


class Cluster(val spatial: SimpleSpatial = SimpleSpatial()) : ArrayList<Instance>(), LifeCycle, Spatial by spatial {

    //    TODO: Bounds for cluster
    override fun isInitialized() = true

    override fun update(seconds: Float) {
        for (i in 0..size - 1) {
            get(i).update(seconds)
        }
    }

    var isHasMoved: Boolean
        get() {
            for (i in 0..size - 1) {
                if (get(i).isHasMoved) {
                    return true
                }
            }
            return false
        }
        set(value) {
            for (i in 0..size - 1) {
                get(i).isHasMoved = value
            }
        }

    var minMaxProperty: AABB = AABB(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX))

    override fun getMinMax() : AABB {
        if(isHasMoved || minMaxProperty.min == Spatial.MIN) {
            recalculate()
        }
        return minMaxProperty
    }

    fun getMinMaxWorld(i: Int) : AABB = get(i).getMinMaxWorld(get(i))

    fun recalculate() {
        minMaxProperty.min.set(Spatial.MIN)
        minMaxProperty.max.set(Spatial.MAX)

        for (i in 0..size - 1) {
            val currentMinMax = get(i).getMinMaxWorld(get(i))
            val currentMin = currentMinMax.min
            val currentMax = currentMinMax.max

            with(minMaxProperty.min) {
                x = Math.min(x, currentMin.x)
                y = Math.min(y, currentMin.y)
                z = Math.min(z, currentMin.z)
            }
            with(minMaxProperty.max) {
                x = Math.max(x, currentMax.x)
                y = Math.max(y, currentMax.y)
                z = Math.max(z, currentMax.z)
            }
        }

        spatial.minMaxWorldProperty.min.set(minMax.min)
        spatial.minMaxWorldProperty.max.set(minMax.max)
        spatial.calculateCenter(spatial.centerWorld, minMaxWorld)
        spatial.calculateBoundSphereRadius()
    }
}
