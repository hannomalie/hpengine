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

    var minMaxProperty: Array<Vector3f> = arrayOf(Vector3f(min), Vector3f(max))

    override fun getMinMax() : Array<Vector3f> {
        if(isHasMoved || minMaxProperty[0] == min) {
            recalculate()
        }
        return minMaxProperty
    }

    public fun recalculate() {
        minMaxProperty[0].set(min)
        minMaxProperty[1].set(max)

        for (i in 0..size - 1) {
            val currentMinMax = get(i).getMinMaxWorld(get(i))
            val currentMin = currentMinMax[0]
            val currentMax = currentMinMax[1]

            with(minMaxProperty[0]) {
                x = Math.min(x, currentMin.x)
                y = Math.min(y, currentMin.y)
                z = Math.min(z, currentMin.z)
            }
            with(minMaxProperty[1]) {
                x = Math.max(x, currentMax.x)
                y = Math.max(y, currentMax.y)
                z = Math.max(z, currentMax.z)
            }
        }

        spatial.minMaxWorldProperty[0].set(minMaxProperty[0])
        spatial.minMaxWorldProperty[1].set(minMaxProperty[1])
        spatial.calculateCenter(spatial.center, minMaxProperty)
        spatial.calculateBoundSphereRadius()
    }
}
