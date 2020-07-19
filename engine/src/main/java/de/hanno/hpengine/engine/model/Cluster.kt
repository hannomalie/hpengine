package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.joml.Vector3f
import java.util.ArrayList


class Cluster(val spatial: SimpleSpatial = SimpleSpatial(AABB(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX)))) : ArrayList<Instance>(), Updatable, Spatial by spatial {

    override fun CoroutineScope.update(deltaSeconds: Float) {
        with(spatial) { update(deltaSeconds) }
        for (i in 0 until size) {
            launch {
                with(get(i)) { update(deltaSeconds) }
            }
        }
    }

    var isHasMoved: Boolean
        get() {
            for (i in 0 until size) {
                if (get(i).isHasMoved) {
                    return true
                }
            }
            return false
        }
        set(value) {
            for (i in 0 until size) {
                get(i).isHasMoved = value
            }
        }

    val minMaxLocal: AABB
        get() {
            if(isHasMoved || spatial.minMaxLocal.min == Spatial.MIN) {
                recalculate()
            }
            return spatial.minMaxLocal
        }


    fun getMinMaxWorld(i: Int) : AABB = get(i).getMinMax(get(i))

    fun recalculate() {
        val minMaxProperty = spatial.minMaxLocal
        minMaxProperty.min.set(Spatial.MIN)
        minMaxProperty.max.set(Spatial.MAX)

        for (i in 0 until size) {
            val currentMinMax = get(i).getMinMax(get(i))
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

        minMaxProperty.min.set(minMaxLocal.min)
        minMaxProperty.max.set(minMaxLocal.max)
        spatial.calculateCenter()
        spatial.calculateBoundSphereRadius()
    }
}
