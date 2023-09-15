package de.hanno.hpengine.graphics.vct

import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.math.Vector3fStrukt
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface VoxelGrid : Strukt {
    context(ByteBuffer) var albedoGrid: Int
    context(ByteBuffer) var normalGrid: Int
    context(ByteBuffer) var grid: Int
    context(ByteBuffer) var indexGrid: Int

    context(ByteBuffer) var _gridSize: Int
    context(ByteBuffer) var _gridSizeHalf: Int
    context(ByteBuffer) val dummy2: Int
    context(ByteBuffer) val dummy3: Int

    context(ByteBuffer) var gridSize
        get() = _gridSize
        set(value) {
            _gridSize = value
            _gridSizeHalf = value / 2
        }
    context(ByteBuffer) val gridSizeHalf
        get() = _gridSizeHalf

    context(ByteBuffer) val projectionMatrix: Matrix4fStrukt

    context(ByteBuffer) val position: Vector3fStrukt
    context(ByteBuffer) var scale: Float

    context(ByteBuffer) var albedoGridHandle: Long
    context(ByteBuffer) var normalGridHandle: Long
    context(ByteBuffer) var gridHandle: Long
    context(ByteBuffer) var indexGridHandle: Long

    context(ByteBuffer) val gridSizeScaled: Int
        get() = (gridSize * scale).toInt()

    context(ByteBuffer) val gridSizeHalfScaled: Int
        get() = (gridSizeHalf * scale).toInt()


    context(ByteBuffer) val currentVoxelTarget: Int
        get() = indexGrid
    context(ByteBuffer) val currentVoxelSource: Int
        get() = grid


    companion object
}
