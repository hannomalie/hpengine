package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.clusters
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.instances
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
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
import org.joml.Matrix4f
import java.io.Serializable
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.EnumSet
import java.util.logging.Logger


class ModelComponent(entity: Entity, val model: Model<*>) : BaseComponent(entity), Serializable, Bufferable {

    var instanced = false

    private val indicesCounts: IntArray
    val baseVertices: IntArray

    init {
        indicesCounts = IntArray(model.meshes.size)
        baseVertices = IntArray(model.meshes.size)
    }

    protected var materialName = ""
    private lateinit var vertexIndexOffsets: VertexIndexOffsets
    var jointsOffset = 0
    @Transient
    var componentIndex = -1
    var entityBufferIndex: Int = 0

    @Transient
    private var materialCache: WeakReference<SimpleMaterial>? = null

    val boundingSphereRadius: Float
        get() = model.boundingSphereRadius

    val indices: IntArray
        get() = model.indices

    val triangleCount: Int
        get() = model.triangleCount

    override val identifier: String
        get() = COMPONENT_KEY

    val indexCount: Int
        get() = indicesCounts[0]

    // TODO: Remove this
    val indexOffset: Int
        get() = vertexIndexOffsets!!.indexOffset

    // TODO: Remove this
    val baseVertex: Int
        get() = vertexIndexOffsets!!.vertexOffset
    val minMax: AABB
        get() {
            if (model is AnimatedModel) {
                return getMinMax(entity)
            }
            return model.minMax
        }

    val meshes: List<Mesh<*>>
        get() = model.meshes

    val isStatic: Boolean
        get() = model.isStatic ?: true

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

    val materials: List<SimpleMaterial>
        get() = meshes.map { it.material }

    fun getMaterial(materialManager: MaterialManager): SimpleMaterial {
        if (materialCache != null && materialCache!!.get() != null && materialCache!!.get()!!.materialInfo.name == materialName) {
            //            return materialCache.get();
        }
        val material = materialManager.getMaterial(materialName)
        materialCache = WeakReference(material)
        if (material == null) {
            Logger.getGlobal().info("SimpleMaterial null, default is applied")
            return materialManager.defaultMaterial
        }
        return material
    }

    fun setMaterial(materialManager: MaterialManager, materialName: String) {
        this.materialName = materialName
        model.setMaterial(materialManager.getMaterial(materialName))
        for (child in entity.children) {
            child.getComponentOption(ModelComponent::class.java).ifPresent { c -> c.setMaterial(materialManager, materialName) }
        }
    }

    fun putToBuffer(gpuContext: GpuContext<*>, vertexIndexBuffer: VertexIndexBuffer, channels: EnumSet<DataChannels>): VertexIndexOffsets {

        val compiledVertices = model.compiledVertices

        val elementsPerVertex = DataChannels.totalElementsPerVertex(channels)
        val indicesCount = indices.size
        vertexIndexOffsets = vertexIndexBuffer.allocate(compiledVertices.size, indicesCount)

        var currentIndexOffset = vertexIndexOffsets!!.indexOffset
        var currentVertexOffset = vertexIndexOffsets!!.vertexOffset

        for (i in indicesCounts.indices) {
            val mesh = model.meshes[i] as Mesh<*>
            indicesCounts[i] = currentIndexOffset
            baseVertices[i] = currentVertexOffset
            currentIndexOffset += mesh.indexBufferValuesArray.size
            currentVertexOffset += mesh.vertexBufferValuesArray.size / elementsPerVertex
        }

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
            shrunk.buffer.copyTo(vertexIndexBuffer.vertexBuffer.buffer, true, vertexIndexOffsets!!.vertexOffset * bytesPerObject)
            vertexIndexBuffer.indexBuffer.appendIndices(vertexIndexOffsets!!.indexOffset, *indices)

            LOGGER.fine("Current IndexOffset: " + vertexIndexOffsets!!.indexOffset)
            LOGGER.fine("Current BaseVertex: " + vertexIndexOffsets!!.vertexOffset)
            vertexIndexBuffer.vertexBuffer.upload()
        }

        return vertexIndexOffsets
    }

    fun getMinMax(transform: Transform<*>): AABB {
        return model.getMinMax(transform)
    }

    fun getIndexCount(i: Int): Int {
        return model.meshIndices[i].size()
    }

    fun getIndexOffset(i: Int): Int {
        return indicesCounts[i]
    }

    fun getBaseVertex(i: Int): Int {
        return baseVertices[i]
    }

    val baseJointIndex: Int
        get() {
            return jointsOffset
        }


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

    override fun putToBuffer(buffer: ByteBuffer) {

        var meshIndex = 0
        val meshes = meshes
        for (mesh in meshes) {
            val materialIndex = mesh.material.materialIndex
            run { putValues(buffer, entity.transformation, meshIndex, materialIndex, animationFrame0, getMinMax(entity, mesh)) }

            for (cluster in entity.clusters) {
                for (i in cluster.indices) {
                    val instance = cluster[i]
                    val instanceMatrix = instance.transformation
                    val instanceMaterialIndex = instance.materials[meshIndex].materialIndex
                    putValues(buffer, instanceMatrix, meshIndex, instanceMaterialIndex, instance.animationController!!.currentFrameIndex, instance.getMinMaxWorld(instance))
                }
            }

            // TODO: This has to be the outer loop i think?
            if (entity.hasParent()) {
                for (instance in entity.instances) {
                    val instanceMatrix = instance.transformation
                    putValues(buffer, instanceMatrix, meshIndex, materialIndex, instance.animationController!!.currentFrameIndex, entity.minMaxWorld)
                }
            }
            meshIndex++
        }
    }

    private fun putValues(buffer: ByteBuffer, mm: Matrix4f, meshIndex: Int, materialIndex: Int, animationFrame0: Int, minMaxWorld: AABB) {
        buffer.putFloat(mm.m00())
        buffer.putFloat(mm.m01())
        buffer.putFloat(mm.m02())
        buffer.putFloat(mm.m03())
        buffer.putFloat(mm.m10())
        buffer.putFloat(mm.m11())
        buffer.putFloat(mm.m12())
        buffer.putFloat(mm.m13())
        buffer.putFloat(mm.m20())
        buffer.putFloat(mm.m21())
        buffer.putFloat(mm.m22())
        buffer.putFloat(mm.m23())
        buffer.putFloat(mm.m30())
        buffer.putFloat(mm.m31())
        buffer.putFloat(mm.m32())
        buffer.putFloat(mm.m33())

        buffer.putInt(if (entity.isSelected) 1 else 0)
        buffer.putInt(materialIndex)
        buffer.putInt(entity.updateType.asDouble.toInt())
        buffer.putInt(entityBufferIndex + meshIndex)

        buffer.putInt(entity.index)
        buffer.putInt(meshIndex)
        buffer.putInt(getBaseVertex(meshIndex))
        buffer.putInt(baseJointIndex)

        buffer.putInt(animationFrame0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putInt(if (isInvertTexCoordY) 1 else 0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        buffer.putFloat(minMaxWorld.min.x)
        buffer.putFloat(minMaxWorld.min.y)
        buffer.putFloat(minMaxWorld.min.z)
        buffer.putFloat(1f)

        buffer.putFloat(minMaxWorld.max.x)
        buffer.putFloat(minMaxWorld.max.y)
        buffer.putFloat(minMaxWorld.max.z)
        buffer.putFloat(1f)
    }

    override fun debugPrintFromBuffer(buffer: ByteBuffer): String {
        return getDebugStringFromBuffer(buffer)
    }

    override fun toString(): String {
        return "ModelComponent (" + model.toString() + ")"
    }

    override fun getBytesPerObject(): Int {
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
