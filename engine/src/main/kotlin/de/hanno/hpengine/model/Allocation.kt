package de.hanno.hpengine.model

import de.hanno.hpengine.scene.VertexIndexOffsets

sealed class Allocation(val forMeshes: List<VertexIndexOffsets>) {
    init {
        require(forMeshes.isNotEmpty())
    }

    val indexOffset = forMeshes.first().indexOffset
    val vertexOffset = forMeshes.first().vertexOffset

    class Static(forMeshes: List<VertexIndexOffsets>) : Allocation(forMeshes)
    class Animated(forMeshes: List<VertexIndexOffsets>, val jointsOffset: Int): Allocation(forMeshes)
}

val Allocation.baseJointIndex: Int
    get() = when (this) {
        is Allocation.Static -> 0
        is Allocation.Animated -> jointsOffset
    }
