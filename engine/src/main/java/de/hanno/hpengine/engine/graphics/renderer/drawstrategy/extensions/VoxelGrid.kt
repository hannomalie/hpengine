package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.graphics.HpMatrix
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.struct.Struct

open class VoxelGrid : Struct() {
    var albedoGrid by 0
    var normalGrid by 0
    var grid by 0
    var indexGrid by 0

    private var _gridSize by 0
    private var _gridSizeHalf by 0
    val dummy2 by 0
    val dummy3 by 0

    var gridSize
        get() = _gridSize
        set(value) {
            _gridSize = value
            _gridSizeHalf = value/2
        }
    val gridSizeHalf
        get() = _gridSizeHalf

    val projectionMatrix by HpMatrix()

    var position by HpVector3f()
    var scale by 0f

    var albedoGridHandle by 0L
    var normalGridHandle by 0L
    var gridHandle by 0L
    var indexGridHandle by 0L

    val gridSizeScaled: Int
        get() = (gridSize * scale).toInt()

    val gridSizeHalfScaled: Int
        get() = (gridSizeHalf * scale).toInt()


    val currentVoxelTarget: Int
        get() = indexGrid
    val currentVoxelSource: Int
        get() = grid



    companion object {
        operator fun invoke(gridSize: Int): VoxelGrid {
            return VoxelGrid().apply {
                this@apply.gridSize = gridSize
            }
        }
    }
}
