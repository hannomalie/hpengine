package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.AnimatedVertexStruct
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets
import de.hanno.hpengine.engine.scene.VertexStruct
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.shrinkToBytes
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer
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

    override val identifier: String
        get() = COMPONENT_KEY

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

    fun getIndexCount(i: Int): Int = model.meshIndices[i].size()

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

    fun debugPrintFromBuffer(buffer: ByteBuffer): String {
        return getDebugStringFromBuffer(buffer)
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

        fun getDebugStringFromBuffer(buffer: ByteBuffer): String {
            val builder = StringBuilder()
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append("\n")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append("\n")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append("\n")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append(" ")
                    .append(buffer.float).append("\n")
            //                .append("Selected ").append(buffer.getInt()).append("\n")
            //                .append("SimpleMaterial Index ").append(buffer.getInt()).append("\n")
            //                .append("Update ").append(buffer.getInt() == 0 ? "Dynamic" : "Static").append("\n")
            //                .append("Mesh Buffer Index ").append(buffer.getInt()).append("\n")
            //                .append("Entity Index ").append(buffer.getInt()).append("\n")
            //                .append("Mesh Index ").append(buffer.getInt()).append("\n")
            //                .append("Base Vertex ").append(buffer.getInt()).append("\n")
            //                .append("Joint Base Index ").append(buffer.getInt()).append("\n")
            //                .append("Animationframe 0 ").append(buffer.getInt()).append("\n")
            //                .append("Animationframe 1 ").append(buffer.getInt()).append("\n")
            //                .append("Animationframe 2 ").append(buffer.getInt()).append("\n")
            //                .append("Animationframe 3 ").append(buffer.getInt()).append("\n")
            //                .append("InvertTexCoordY ").append(buffer.getInt()).append("\n")
            //                .append("Placeholder ").append(buffer.getInt()).append("\n")
            //                .append("Placeholder ").append(buffer.getInt()).append("\n")
            //                .append("Placeholder ").append(buffer.getInt()).append("\n")
            //                .append("MinX ").append(buffer.getInt()).append("\n")
            //                .append("MinY ").append(buffer.getInt()).append("\n")
            //                .append("MinZ ").append(buffer.getInt()).append("\n")
            //                .append("Placeholder ").append(buffer.getInt()).append("\n")
            //                .append("MaxX ").append(buffer.getInt()).append("\n")
            //                .append("MaxY ").append(buffer.getInt()).append("\n")
            //                .append("MaxZ ").append(buffer.getInt()).append("\n")
            //                .append("Placeholder ").append(buffer.getInt()).append("\n");

            //        System.out.println(resultString);
            return builder.toString()
        }

        val bytesPerInstance: Int
            get() = 16 * java.lang.Float.BYTES + 16 * Integer.BYTES + 8 * java.lang.Float.BYTES
    }
}

fun VertexIndexBuffer.allocateForComponent(modelComponent: ModelComponent): VertexIndexOffsets {
    return allocate(modelComponent.model.compiledVertices.size, modelComponent.indices.size)
}
fun ModelComponent.putToBuffer(gpuContext: GpuContext<*>,
                               vertexIndexBuffer: VertexIndexBuffer,
                               channels: EnumSet<DataChannels>,
                               vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {

    val compiledVertices = model.compiledVertices

    val elementsPerVertex = DataChannels.totalElementsPerVertex(channels)

    val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets, elementsPerVertex)

    val result: StructArray<*>
    val bytesPerObject: Int
    if (model.isStatic) {
        val converted = StructArray(compiledVertices.size) { VertexStruct() }
        for (i in compiledVertices.indices) {
            val (_, position, texCoord, normal) = compiledVertices[i] as Vertex
            val target = converted.getAtIndex(i)
            target.position.set(position)
            target.texCoord.set(texCoord)
            target.normal.set(normal)
        }
        result = converted
        bytesPerObject = Vertex.sizeInBytes
    } else {
        val converted = StructArray(compiledVertices.size) { AnimatedVertexStruct() }
        for (i in compiledVertices.indices) {
            val (_, position, texCoord, normal, weights, jointIndices) = compiledVertices[i] as AnimatedVertex
            val target = converted.getAtIndex(i)
            target.position.set(position)
            target.texCoord.set(texCoord)
            target.normal.set(normal)
            target.weights.set(weights)
            target.jointIndices.set(jointIndices)
        }
        result = converted
        bytesPerObject = AnimatedVertex.sizeInBytes
    }

    gpuContext.execute("ModelComponent.putToBuffer") {
        vertexIndexBuffer.vertexBuffer.setCapacityInBytes(bytesPerObject * compiledVertices.size)
        val shrunk = result.shrinkToBytes(vertexIndexBuffer.vertexBuffer.buffer.capacity(), true)
        shrunk.buffer.copyTo(vertexIndexBuffer.vertexBuffer.buffer, true, vertexIndexOffsets.vertexOffset * bytesPerObject)
        vertexIndexBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, *indices)
        vertexIndexBuffer.vertexBuffer.upload()
    }

    return vertexIndexOffsetsForMeshes
}

fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets, elementsPerVertex: Int): List<VertexIndexOffsets> {
    var currentIndexOffset = vertexIndexOffsets.indexOffset
    var currentVertexOffset = vertexIndexOffsets.vertexOffset

    val vertexIndexOffsets = model.meshes.indices.map { i ->
        val mesh = model.meshes[i] as Mesh<*>
        VertexIndexOffsets(currentIndexOffset, currentVertexOffset).apply {
            currentIndexOffset += mesh.indexBufferValuesArray.size
            currentVertexOffset += mesh.vertexBufferValuesArray.size / elementsPerVertex
        }
    }
    return vertexIndexOffsets
}
