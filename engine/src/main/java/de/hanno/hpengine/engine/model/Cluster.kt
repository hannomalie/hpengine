package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.joml.Vector3f
import java.util.ArrayList


class Cluster : ArrayList<Instance>(), Updatable {
    var boundingVolume: AABB = AABB(AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX)))
        private set

    var recalculatedInCycle = -1L
        private set

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        (0 until size).map { i ->
            with(get(i)) {
                update(scene, deltaSeconds)
            }
        }
        recalculate(scene.currentCycle)
    }

    private fun recalculate(currentCycle: Long) {
        if(all { it.spatial is StaticTransformSpatial } && (boundingVolume.min != Vector3f(Spatial.MAX) || boundingVolume.max != Vector3f(Spatial.MIN))) {
            return
        }
        boundingVolume.localAABB = map { it.boundingVolume }.getSurroundingAABB()
        recalculatedInCycle = currentCycle
    }
}
