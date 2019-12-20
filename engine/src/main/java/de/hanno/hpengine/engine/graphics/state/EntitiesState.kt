package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.component.ModelComponent.Companion.DEFAULTANIMATEDCHANNELS
import de.hanno.hpengine.engine.component.ModelComponent.Companion.DEFAULTCHANNELS
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch.RenderBatches
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.model.material.MaterialStruct
import de.hanno.hpengine.engine.scene.BatchKey
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.StructArray
import java.util.HashMap

class EntitiesState(gpuContext: GpuContext<*>) {
    val cash: MutableMap<BatchKey, RenderBatch> = HashMap()
    var entityMovedInCycle: Long = 0
    var staticEntityMovedInCycle: Long = 0
    var entityAddedInCycle: Long = 0
    var renderBatchesStatic = RenderBatches()
    var renderBatchesAnimated = RenderBatches()
    var vertexIndexBufferStatic = VertexIndexBuffer(gpuContext, 10, 10, DEFAULTCHANNELS)
    var vertexIndexBufferAnimated = VertexIndexBuffer(gpuContext, 10, 10, DEFAULTANIMATEDCHANNELS)
    var entitiesBuffer = PersistentMappedBuffer(gpuContext, 8000)
    var jointsBuffer = PersistentMappedBuffer(gpuContext, 8000)
    var joints: List<BufferableMatrix4f>? = null
    val materialBuffer = PersistentMappedStructBuffer(0, { MaterialStruct() }, gpuContext)

}