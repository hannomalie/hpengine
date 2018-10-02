package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.HpMatrix
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.hpengine.engine.transform.SimpleTransform
import de.hanno.hpengine.engine.transform.TransformSpatial
import de.hanno.hpengine.util.Util
import de.hanno.struct.Struct
import org.joml.Vector3f

open class VoxelGrid protected constructor(): Struct() {
    var albedoGrid by 0
    var normalGrid by 0
    var grid by 0
    var grid2 by 0

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

    val projectionMatrix by HpMatrix(this)

    var position by HpVector3f(this)
    private var scaleProperty by 0f

    var albedoGridHandle by 0L
    var normalGridHandle by 0L
    var gridHandle by 0L
    var grid2Handle by 0L

    private val transform = SimpleTransform()
    val spatial = TransformSpatial(transform)
    var scale: Float = 0f
        get() = scaleProperty
        set(value) {
            if(field != value) {
                field = value
                scaleProperty = value
//                transform.scale(value)
                initCam()
            }
        }

    val gridSizeScaled: Int
        get() = (gridSize * scaleProperty).toInt()

    val gridSizeHalfScaled: Int
        get() = (gridSizeHalf * scaleProperty).toInt()

    open val currentVoxelSource: Int
        get() = grid


    val entity = Entity("VCTCam")
    var orthoCam = Camera(entity, createOrthoMatrix(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), 90f, 1f)

    init {
        initCam()
    }

    private fun initCam(): Camera {
        entity.set(transform)
        this.orthoCam = Camera(entity, createOrthoMatrix(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), 90f, 1f)
        orthoCam.perspective = false
        orthoCam.width = gridSizeScaled.toFloat()
        orthoCam.height = gridSizeScaled.toFloat()
        orthoCam.setFar(gridSizeScaled.toFloat())
        orthoCam.setNear(-gridSizeScaled.toFloat())
        orthoCam.update(0.000001f)
        projectionMatrix.set(orthoCam.projectionMatrix)
        return orthoCam
    }

    fun setPosition(position: Vector3f) {
        this.position.set(position)
        this.transform.identity().translate(position)
    }
    private val tempTranslation = Vector3f()
    fun move(amount: Vector3f) {
        transform.translate(amount)
        position.set(transform.getTranslation(tempTranslation))
    }

    fun createOrthoMatrix() =
            Util.createOrthogonal((-gridSizeScaled).toFloat(), gridSizeScaled.toFloat(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat(), gridSizeScaled.toFloat(), (-gridSizeScaled).toFloat())
    companion object {
        operator fun invoke(gridSize: Int): VoxelGrid {
            return VoxelGrid().apply {
                this@apply.gridSize = gridSize
            }
        }
    }
}

class TwoBounceVoxelGrid(gridSize: Int): VoxelGrid() {

    override var currentVoxelSource: Int = 0
    var currentVoxelTarget: Int = 0

    fun switchCurrentVoxelGrid() {
        if (currentVoxelTarget == grid) {
            currentVoxelTarget = grid2
            currentVoxelSource = grid
        } else {
            currentVoxelTarget = grid
            currentVoxelSource = grid2
        }
    }

    init {
        this.gridSize = gridSize
    }
}