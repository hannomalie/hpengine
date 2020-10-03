package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.RenderBatches
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.material.MaterialStruct
import de.hanno.hpengine.engine.scene.BatchKey
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import java.util.HashMap

class EntitiesState(gpuContext: GpuContext<*>) {
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
    var entitiesBuffer = PersistentMappedStructBuffer(0, gpuContext, { EntityStruct() })
    var jointsBuffer = PersistentMappedStructBuffer(0, gpuContext, { Matrix4f() })
    val materialBuffer = PersistentMappedStructBuffer(0, gpuContext, { MaterialStruct() })

}