package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.absoluteMaximum
import de.hanno.hpengine.engine.transform.absoluteMinimum
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.joml.Vector3f
import java.util.ArrayList


class Cluster(minMax: AABBData = AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX))) : ArrayList<Instance>(), Updatable {
    var minMax: AABBData = minMax
        private set

    override fun CoroutineScope.update(deltaSeconds: Float) {
        for (i in 0 until size) {
            launch {
                with(get(i)) { update(deltaSeconds) }
            }
        }

        // TODO: Recalculate minMax somehow here
    }

    fun getMinMaxWorld(i: Int) : AABB = get(i).getMinMax(get(i).transform)

    private fun recalculate() {
        val minMaxProperty = minMax

        val minResult = Vector3f(absoluteMaximum)
        val maxResult = Vector3f(absoluteMinimum)

        for (i in 0 until size) {
            val currentMinMax = get(i).getMinMax(get(i).transform)
            val currentMin = currentMinMax.min
            val currentMax = currentMinMax.max

            minResult.min(currentMin)
            maxResult.max(currentMax)
        }

        minMax = AABBData(minResult, maxResult)
    }
}
