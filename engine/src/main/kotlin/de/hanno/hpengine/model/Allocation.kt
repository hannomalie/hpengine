package de.hanno.hpengine.model

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.scene.GeometryOffset
import de.hanno.hpengine.scene.VertexIndexOffsets

sealed class Allocation(val forMeshes: List<GeometryOffset>) {
    init {
        require(forMeshes.isNotEmpty())
    }

    val indexOffset = (forMeshes.first() as? VertexIndexOffsets)?.indexOffset ?: ElementCount(-1)
    val vertexOffset = forMeshes.first().vertexOffset

    class Static(forMeshes: List<GeometryOffset>) : Allocation(forMeshes)
    class Animated(forMeshes: List<GeometryOffset>, val jointsOffset: Int): Allocation(forMeshes)
}

val Allocation.baseJointIndex: Int
    get() = when (this) {
        is Allocation.Static -> 0
        is Allocation.Animated -> jointsOffset
    }
