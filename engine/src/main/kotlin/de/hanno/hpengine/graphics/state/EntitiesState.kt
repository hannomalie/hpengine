package de.hanno.hpengine.graphics.state

import EntityStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.type
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.RenderBatches
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBufferAllocator
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.scene.BatchKey
import de.hanno.hpengine.scene.VertexIndexBuffer
import java.util.HashMap

context(GraphicsApi)
class EntitiesState {
    private val ssboAllocator = PersistentMappedBufferAllocator()

    val cash: MutableMap<BatchKey, RenderBatch> = HashMap()
    var entityMovedInCycle: Long = -1
    var staticEntityMovedInCycle: Long = -1
    val anyEntityMovedInCycle: Long
        get() = if(entityMovedInCycle >= staticEntityMovedInCycle) entityMovedInCycle else staticEntityMovedInCycle
    var entityAddedInCycle: Long = -1
    var componentAddedInCycle: Long = -1
    var renderBatchesStatic = RenderBatches()
    var renderBatchesAnimated = RenderBatches()
    var vertexIndexBufferStatic = VertexIndexBuffer(10)
    var vertexIndexBufferAnimated = VertexIndexBuffer(10)

    var entitiesBuffer = PersistentShaderStorageBuffer(EntityStrukt.type.sizeInBytes).typed(EntityStrukt.type)
    var jointsBuffer = PersistentShaderStorageBuffer(Matrix4fStrukt.type.sizeInBytes).typed(Matrix4fStrukt.type)
    val materialBuffer = PersistentShaderStorageBuffer(MaterialStrukt.type.sizeInBytes).typed(MaterialStrukt.type)

}