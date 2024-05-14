package de.hanno.hpengine.graphics.state

import AnimatedVertexStruktPackedImpl.Companion.type
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.BatchKey
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.scene.VertexStruktPacked

class EntitiesState(graphicsApi: GraphicsApi) {
    val cash: MutableMap<BatchKey, RenderBatch> = HashMap()
    var entityMovedInCycle: Long = -1
    var staticEntityMovedInCycle: Long = -1
    val anyEntityMovedInCycle: Long
        get() = if(entityMovedInCycle >= staticEntityMovedInCycle) entityMovedInCycle else staticEntityMovedInCycle
    var entityAddedInCycle: Long = -1
    var componentAddedInCycle: Long = -1

    var vertexIndexBufferStatic = VertexIndexBuffer(graphicsApi, VertexStruktPacked.type, 10)
    var vertexIndexBufferAnimated = VertexIndexBuffer(graphicsApi, AnimatedVertexStruktPacked.type, 10)

    var jointsBuffer = graphicsApi.PersistentShaderStorageBuffer(Matrix4fStrukt.type.sizeInBytes).typed(Matrix4fStrukt.type)
}