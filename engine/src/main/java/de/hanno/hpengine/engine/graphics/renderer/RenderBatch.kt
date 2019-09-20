package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommandXXX
import de.hanno.hpengine.engine.model.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.struct.Struct
import org.joml.Vector3f

import java.util.ArrayList

class RenderBatch: Struct() {
    var isVisible by false
    var isSelected by false
    var isDrawLines by false
    var minWorld by de.hanno.hpengine.engine.math.Vector3f()
    var maxWorld by de.hanno.hpengine.engine.math.Vector3f()
    var cameraWorldPosition by de.hanno.hpengine.engine.math.Vector3f()
    var isInReachForTextureLoading by false
    var isVisibleForCamera by false
    var update by Update::class.java
    val drawElementsIndirectCommand by DrawElementsIndirectCommandXXX()
    var centerWorld by de.hanno.hpengine.engine.math.Vector3f()
    private var animated by false
    var boundingSphereRadius by 0.0f
    private val instanceMinMaxWorlds = ArrayList<AABB>()
    var materialInfo: MaterialInfo = SimpleMaterialInfo("Dummy")
        private set

    val entityBufferIndex: Int
        get() = drawElementsIndirectCommand.entityOffset

    val instanceCount: Int
        get() = drawElementsIndirectCommand.primCount


    val indexCount: Int
        get() = drawElementsIndirectCommand.count

    val indexOffset: Int
        get() = drawElementsIndirectCommand.firstIndex

    val baseVertex: Int
        get() = drawElementsIndirectCommand.baseVertex

    val vertexCount: Int
        get() = drawElementsIndirectCommand.count / 3

    val isStatic: Boolean
        get() = !animated

    fun init(entityBaseIndex: Int, isVisible: Boolean, isSelected: Boolean, drawLines: Boolean, cameraWorldPosition: Vector3f, isInReachForTextureStreaming: Boolean, instanceCount: Int, visibleForCamera: Boolean, update: Update, minWorld: Vector3f, maxWorld: Vector3f, centerWorld: Vector3f, boundingSphereRadius: Float, indexCount: Int, indexOffset: Int, baseVertex: Int, animated: Boolean, instanceMinMaxWorlds: List<AABB>, materialInfo: MaterialInfo): RenderBatch {
        this.isVisible = isVisible
        this.isSelected = isSelected
        this.isDrawLines = drawLines
        this.cameraWorldPosition.set(cameraWorldPosition)
        this.isInReachForTextureLoading = isInReachForTextureStreaming
        this.isVisibleForCamera = visibleForCamera
        this.update = update
        this.minWorld.set(minWorld)
        this.maxWorld.set(maxWorld)
        this.instanceMinMaxWorlds.clear()
        this.instanceMinMaxWorlds.addAll(instanceMinMaxWorlds)
        this.boundingSphereRadius = boundingSphereRadius
        this.centerWorld.set(centerWorld)
        this.drawElementsIndirectCommand.count = indexCount
        this.drawElementsIndirectCommand.primCount = instanceCount
        this.drawElementsIndirectCommand.firstIndex = indexOffset
        this.drawElementsIndirectCommand.baseVertex = baseVertex
        this.drawElementsIndirectCommand.baseInstance = 0
        this.drawElementsIndirectCommand.entityOffset = entityBaseIndex
        this.animated = animated
        this.materialInfo = materialInfo
        return this
    }

    fun getInstanceMinMaxWorlds(): List<AABB> {
        return instanceMinMaxWorlds
    }

    class RenderBatches : ArrayList<RenderBatch>()
}