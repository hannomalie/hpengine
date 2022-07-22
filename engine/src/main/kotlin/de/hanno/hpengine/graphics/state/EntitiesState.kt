package de.hanno.hpengine.graphics.state

import EntityStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.type
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.RenderBatches
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBufferAllocator
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.scene.BatchKey
import de.hanno.hpengine.scene.VertexIndexBuffer
import java.util.HashMap

class EntitiesState(gpuContext: GpuContext<*>) {
    private val ssboAllocator = PersistentMappedBufferAllocator(gpuContext)

    val cash: MutableMap<BatchKey, RenderBatch> = HashMap()
    var entityMovedInCycle: Long = -1
    var staticEntityMovedInCycle: Long = -1
    val anyEntityMovedInCycle: Long
        get() = if(entityMovedInCycle >= staticEntityMovedInCycle) entityMovedInCycle else staticEntityMovedInCycle
    var entityAddedInCycle: Long = -1
    var componentAddedInCycle: Long = -1
    var renderBatchesStatic = RenderBatches()
    var renderBatchesAnimated = RenderBatches()
    var vertexIndexBufferStatic = VertexIndexBuffer(gpuContext, 10)
    var vertexIndexBufferAnimated = VertexIndexBuffer(gpuContext, 10)

    var entitiesBuffer = PersistentMappedBuffer(EntityStrukt.type.sizeInBytes, gpuContext).typed(EntityStrukt.type)
    var jointsBuffer = PersistentMappedBuffer(Matrix4fStrukt.type.sizeInBytes, gpuContext).typed(Matrix4fStrukt.type)
    val materialBuffer = PersistentMappedBuffer(MaterialStrukt.type.sizeInBytes, gpuContext).typed(MaterialStrukt.type)

}