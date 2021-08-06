package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import de.hanno.hpengine.engine.vertexbuffer.DataChannels
import de.hanno.struct.StructArray
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.util.EnumSet


class ModelComponent @JvmOverloads constructor(entity: Entity, val model: Model<*>, initMaterial: Material = model.material) : BaseComponent(entity) {
    val spatial: TransformSpatial = TransformSpatial(entity.transform, getBoundingVolume(entity.transform))
    var material = initMaterial
        set(value) {
            field = value
            model.material = value
            for (child in entity.children) {
                child.getComponent(ModelComponent::class.java)?.let { c -> c.material = value }
            }
        }
    var instanced = false

    val boundingSphereRadius: Float
        get() = model.boundingSphereRadius

    val indices: StructArray<IntStruct>
        get() = model.indices

    val triangleCount: Int
        get() = model.triangleCount

    val boundingVolume: AABB
        get() = getBoundingVolume(entity.transform)

    val meshes: List<Mesh<*>>
        get() = model.meshes

    val isStatic: Boolean
        get() = model.isStatic

    val animationFrame0: Int
        get() = if (model is AnimatedModel) {
            model.animationController.currentFrameIndex
        } else 0

    var wasUpdated: Boolean
        get() = if (model is AnimatedModel) {
            model.animationController.wasUpdated
        } else false
        set(value) {
            if (model is AnimatedModel) {
                model.animationController.wasUpdated = value
            }
        }

    val isInvertTexCoordY: Boolean
        get() = model.isInvertTexCoordY

    val materials: List<Material>
        get() = meshes.map { it.material }

    init {
        entity.spatial = spatial

        if (model is AnimatedModel) {
            entity.updateType = Update.DYNAMIC
        }
    }

    fun getBoundingVolume(transform: Transform): AABB = model.getBoundingVolume(transform)

    fun getIndexCount(i: Int): Int = model.meshIndexCounts[i]

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        if (this@ModelComponent.model is AnimatedModel) {
            this@ModelComponent.model.update(deltaSeconds)
        }
    }

    fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return model.getBoundingSphereRadius(mesh)
    }

    fun getBoundingVolume(transform: Transform, mesh: Mesh<*>): AABB {
        return model.getBoundingVolume(transform, mesh)
    }

    override fun toString(): String = "ModelComponent [${model.path}]"

    companion object {
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

        fun Entity.modelComponent(model: AnimatedModel) {
            addComponent(ModelComponent(this, model, model.material))
        }
        fun Entity.modelComponent(model: StaticModel) {
            addComponent(ModelComponent(this, model, model.material))
        }

        fun Entity.modelComponent(
            name: String,
            file: String,
            textureManager: TextureManager,
            directory: AbstractDirectory,
            aabb: AABBData? = null
        ): ModelComponent {

            val loadedModel = LoadModelCommand(
                file,
                name,
                textureManager,
                directory,
                this
            ).execute().entities.first().components.firstIsInstance<ModelComponent>().model

            val modelComponent = ModelComponent(this, loadedModel, loadedModel.material)
            aabb?.let { modelComponent.spatial.boundingVolume.localAABB = it.copy() }
            return modelComponent
        }
    }

}

fun VertexIndexBuffer.allocateForComponent(modelComponent: ModelComponent): VertexIndexOffsets {
    return allocate(modelComponent.model.uniqueVertices.size, modelComponent.indices.size)
}
fun ModelComponent.putToBuffer(gpuContext: GpuContext<*>,
                               indexBuffer: VertexIndexBuffer,
                               vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {

    synchronized(indexBuffer) {
        val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)

        if(model is StaticModel) {
            indexBuffer.vertexStructArray.addAll(vertexIndexOffsets.vertexOffset, model.verticesStructArrayPacked.buffer)
        } else if(model is AnimatedModel) {
            indexBuffer.animatedVertexStructArray.addAll(vertexIndexOffsets.vertexOffset, model.verticesStructArrayPacked.buffer)
        } else throw IllegalStateException("Unsupported mode") // TODO: sealed classes!!

//        TODO: Does this have to be on gpu thread?
//        gpuContext.execute("ModelComponent.putToBuffer") {
            indexBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, indices)
//        }

        return vertexIndexOffsetsForMeshes
    }
}

fun ModelComponent.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
    var currentIndexOffset = vertexIndexOffsets.indexOffset
    var currentVertexOffset = vertexIndexOffsets.vertexOffset

    val meshVertexIndexOffsets = model.meshes.indices.map { i ->
        val mesh = model.meshes[i] as Mesh<*>
        VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
            currentIndexOffset += mesh.indexBufferValues.size
            currentVertexOffset += mesh.vertices.size
        }
    }
    return meshVertexIndexOffsets
}
