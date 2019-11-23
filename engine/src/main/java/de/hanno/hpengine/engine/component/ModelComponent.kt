package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.shrinkToBytes
import kotlinx.coroutines.CoroutineScope
import java.lang.IllegalStateException
import java.util.EnumSet
import java.util.logging.Logger


class ModelComponent(entity: Entity, val model: Model<*>, initMaterial: Material) : BaseComponent(entity) {
    var material = initMaterial
        set(value) {
            field = value
            model.setMaterial(value)
            for (child in entity.children) {
                child.getComponentOption(ModelComponent::class.java).ifPresent { c -> c.material = value }
            }
        }
    var instanced = false

    val boundingSphereRadius: Float
        get() = model.boundingSphereRadius

    val indices: IntArray
        get() = model.indices

    val triangleCount: Int
        get() = model.triangleCount

    val minMax: AABB
        get() = getMinMax(entity)

    val meshes: List<Mesh<*>>
        get() = model.meshes

    val isStatic: Boolean
        get() = model.isStatic

    val animationFrame0: Int
        get() = if (model is AnimatedModel) {
            model.animationController.currentFrameIndex
        } else 0

    var isHasUpdated: Boolean
        get() = if (model is AnimatedModel) {
            model.animationController.isHasUpdated
        } else false
        set(value) {
            if (model is AnimatedModel) {
                model.animationController.isHasUpdated = value
            }
        }

    val isInvertTexCoordY: Boolean
        get() = model.isInvertTexCoordY

    val materials: List<Material>
        get() = meshes.map { it.material }

    fun getMinMax(transform: Transform<*>): AABB = model.getMinMax(transform)

    fun getIndexCount(i: Int): Int = model.meshIndices[i].size

    override fun CoroutineScope.update(deltaSeconds: Float) {
        if (model is AnimatedModel) {
            model.update(deltaSeconds)
        }
    }

    fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return model.getBoundingSphereRadius(mesh)
    }

    fun getMinMax(transform: Transform<*>, mesh: Mesh<*>): AABB {
        return model.getMinMax(transform, mesh)
    }

    override fun toString(): String {
        return "ModelComponent (" + model.toString() + ")"
    }

    fun getBytesPerObject(): Int {
        return bytesPerInstance * meshes.size * entity.instanceCount
    }

    companion object {
        val COMPONENT_KEY = ModelComponent::class.java.simpleName
        private val LOGGER = Logger.getLogger(ModelComponent::class.java.name)
        private const val serialVersionUID = 1L

        var DEFAULTCHANNELS = EnumSet.of(
                DataChannels.POSITION3,
                DataChannels.TEXCOORD,
                DataChannels.NORMAL)
        var DEFAULTANIMATEDCHANNELS = EnumSet.of(
                DataChannels.POSITION3,
                DataChannels.TEXCOORD,
                DataChannels.NORMAL,
                DataChannels.WEIGHTS,
                DataChannels.JOINT_INDICES)
        var DEPTHCHANNELS = EnumSet.of(
                DataChannels.POSITION3,
                DataChannels.NORMAL
        )
        var SHADOWCHANNELS = EnumSet.of(
                DataChannels.POSITION3)
        var POSITIONCHANNEL = EnumSet.of(
                DataChannels.POSITION3)

        val bytesPerInstance: Int
            get() = 16 * java.lang.Float.BYTES + 16 * Integer.BYTES + 8 * java.lang.Float.BYTES
    }
}

fun VertexIndexBuffer.allocateForComponent(modelComponent: ModelComponent): VertexIndexOffsets {
    return allocate(modelComponent.model.compiledVertices.size, modelComponent.indices.size)
}
fun ModelComponent.putToBuffer(gpuContext: GpuContext<*>,
                               vertexIndexBuffer: VertexIndexBuffer,
                               vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {

    synchronized(vertexIndexBuffer) {
        val compiledVertices = model.compiledVertices

        val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)

        val result: StructArray<*> = model.verticesStructArray
        val bytesPerObject = model.bytesPerVertex

        if(model is StaticModel) {
            val sizeBefore = vertexIndexBuffer.vertexArray.size
            vertexIndexBuffer.vertexArray.addAll(model.compiledVertices)
            vertexIndexBuffer.vertexStructArray.enlarge(vertexIndexBuffer.vertexArray.size)
            model.verticesStructArray.buffer.copyTo(vertexIndexBuffer.vertexStructArray.buffer, targetOffset = sizeBefore * bytesPerObject)
        } else if(model is AnimatedModel) {
            val sizeBefore = vertexIndexBuffer.animatedVertexArray.size
            vertexIndexBuffer.animatedVertexArray.addAll(model.compiledVertices)
            vertexIndexBuffer.vertexStructArray.enlarge(vertexIndexBuffer.animatedVertexArray.size)
            model.verticesStructArray.buffer.copyTo(vertexIndexBuffer.animatedVertexStructArray.buffer, targetOffset = sizeBefore * bytesPerObject)
        }

        gpuContext.execute("ModelComponent.putToBuffer") {
            val neededSizeInBytes = bytesPerObject * compiledVertices.size
            val vertexOffsetInBytes = vertexIndexOffsets.vertexOffset * bytesPerObject
            vertexIndexBuffer.vertexBuffer.ensureCapacityInBytes(vertexOffsetInBytes + neededSizeInBytes)
            result.buffer.copyTo(vertexIndexBuffer.vertexBuffer.buffer, true, vertexOffsetInBytes)
            vertexIndexBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, *indices)
            vertexIndexBuffer.vertexBuffer.upload()
        }

        return vertexIndexOffsetsForMeshes
    }
}

fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
    var currentIndexOffset = vertexIndexOffsets.indexOffset
    var currentVertexOffset = vertexIndexOffsets.vertexOffset

    val meshVertexIndexOffsets = model.meshes.indices.map { i ->
        val mesh = model.meshes[i] as Mesh<*>
        VertexIndexOffsets(currentIndexOffset, currentVertexOffset).apply {
            currentIndexOffset += mesh.indexBufferValues.size
            currentVertexOffset += mesh.compiledVertices.size
        }
    }
    return meshVertexIndexOffsets
}
