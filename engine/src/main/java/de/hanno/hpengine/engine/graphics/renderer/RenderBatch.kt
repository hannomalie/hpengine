package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.transform.AABB
import org.joml.Vector3f

import java.util.ArrayList

class RenderBatch {
    var entityIndex = -1
    var isVisible = false
    var isSelected = false
    var isDrawLines = false
    var minWorld = Vector3f()
    var maxWorld = Vector3f()
    var cameraWorldPosition = Vector3f()
    var isInReachForTextureLoading = false
    var isVisibleForCamera = false
    var update = Update.STATIC
    val drawElementsIndirectCommand = de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand()
    var centerWorld = Vector3f()
    private var animated = false
    var boundingSphereRadius = 0.0f
    private val instanceMinMaxWorlds = ArrayList<AABB>()
    var materialInfo: MaterialInfo = SimpleMaterialInfo("Dummy")
        private set

    var entityBufferIndex: Int = 0

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

    fun init(entityBufferIndex: Int, isVisible: Boolean, isSelected: Boolean, drawLines: Boolean, cameraWorldPosition: Vector3f, isInReachForTextureStreaming: Boolean, instanceCount: Int, visibleForCamera: Boolean, update: Update, minWorld: Vector3f, maxWorld: Vector3f, centerWorld: Vector3f, boundingSphereRadius: Float, indexCount: Int, indexOffset: Int, baseVertex: Int, animated: Boolean, instanceMinMaxWorlds: List<AABB>, materialInfo: MaterialInfo, entityIndex: Int): RenderBatch {
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
        this.entityBufferIndex = entityBufferIndex
        this.animated = animated
        this.materialInfo = materialInfo
        this.entityIndex = entityIndex
        return this
    }

    fun getInstanceMinMaxWorlds(): List<AABB> {
        return instanceMinMaxWorlds
    }

    class RenderBatches : ArrayList<RenderBatch>()
}