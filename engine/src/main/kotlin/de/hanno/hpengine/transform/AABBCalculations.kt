package de.hanno.hpengine.transform

import de.hanno.hpengine.artemis.SpatialComponent
import org.joml.Vector3f


fun List<SpatialComponent>.calculateAABB(): AABBData {
    val minResult = Vector3f(absoluteMaximum)
    val maxResult = Vector3f(absoluteMinimum)
    forEach {
        minResult.min(it.spatial.boundingVolume.min)
        maxResult.max(it.spatial.boundingVolume.max)
    }
    return AABBData(minResult, maxResult)
}