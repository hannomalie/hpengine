package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.engine.transform.Spatial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.joml.Vector3f
import java.util.ArrayList


class Cluster(_boundingVolume: AABBData = AABBData(Vector3f(Spatial.MIN), Vector3f(Spatial.MAX))) : ArrayList<Instance>(), Updatable {
    var boundingVolume: AABBData = _boundingVolume
        private set

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        for (i in 0 until size) {
            launch {
                with(get(i)) { update(scene, deltaSeconds) }
            }
        }
        recalculate()
    }

    private fun recalculate() {
        boundingVolume = map { it.boundingVolume.localAABB }.getSurroundingAABB()
    }
}
