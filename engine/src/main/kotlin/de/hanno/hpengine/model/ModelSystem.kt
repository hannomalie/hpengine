package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.utils.IntBag
import de.hanno.hpengine.artemis.forEach
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.buffer.vertex.appendIndices
import de.hanno.hpengine.graphics.renderer.forward.DefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.shader.ProgramImpl
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.instancing.InstanceComponent
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.scene.VertexIndexOffsets
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.resolve.resolveQualifierAsReceiverInExpression
import org.joml.Matrix4f
import org.koin.core.annotation.Single
import struktgen.api.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@Single(binds = [BaseSystem::class, Extractor::class, ModelSystem::class])
@One(
    ModelComponent::class
)
class ModelSystem(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val textureManager: OpenGLTextureManager,
    private val materialSystem: MaterialSystem,
    private val programManager: ProgramManager,
    private val entitiesStateHolder: EntitiesStateHolder,
) : BaseEntitySystem(), Extractor {
    private val logger = LogManager.getLogger(ModelSystem::class.java)
    private val threadPool = Executors.newFixedThreadPool(1)

    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

    val vertexIndexBufferStatic = VertexIndexBuffer(graphicsApi, VertexStruktPacked.type, 10)
    val vertexIndexBufferAnimated = VertexIndexBuffer(graphicsApi, AnimatedVertexStruktPacked.type, 10)

    val joints: MutableList<Matrix4f> = CopyOnWriteArrayList()

    val allocations: MutableMap<ModelComponentDescription, Allocation> = mutableMapOf()

    private val modelCache = mutableMapOf<ModelComponentDescription, Model<*>>()
    internal val programCache = mutableMapOf<ProgramDescription, ProgramImpl<DefaultUniforms>>()
    private val staticModelLoader = StaticModelLoader()
    private val animatedModelLoader = AnimatedModelLoader()

    operator fun get(modelComponentDescription: ModelComponentDescription) = modelCache[modelComponentDescription]

    override fun inserted(entityId: Int) {
        threadPool.submit {
            try {
                loadModelToCache(entityId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun inserted(entities: IntBag) {
        val entities = IntBag().apply { addAll(entities) }
        threadPool.submit {
            entities.forEach { entityId ->
                try {
                    loadModelToCache(entityId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadModelToCache(entityId: Int): ModelCacheComponent {
        logger.info("loading model to cache for entity $entityId")
        val modelComponentOrNull = modelComponentMapper.getOrNull(entityId)
        val instanceComponent = instanceComponentMapper.getOrNull(entityId)

        val materialComponentOrNull = materialComponentMapper.getOrNull(entityId)

        val modelComponent = modelComponentOrNull ?: modelComponentMapper[instanceComponent!!.targetEntity]
        val descr = modelComponent.modelComponentDescription
        logger.info("model description: $descr")
        val dir = when (descr.directory) {
            Directory.Game -> config.gameDir
            Directory.Engine -> config.engineDir
        }
        val model = modelCache.computeIfAbsent(descr) {
            when (descr) {
                is AnimatedModelComponentDescription -> animatedModelLoader.load(descr.file, textureManager, dir)
                is StaticModelComponentDescription -> staticModelLoader.load(descr.file, textureManager, dir)
            }.apply {
                meshes.forEach { mesh ->
                    val meshMaterial = materialComponentOrNull?.material ?: mesh.material
                    materialSystem.registerMaterial(meshMaterial)
                    meshMaterial.programDescription?.let { programDescription ->
                        programCache[programDescription] =
                            programManager.getFirstPassProgram(
                                programDescription,
                                StaticDefaultUniforms(graphicsApi)
                            ) as ProgramImpl<DefaultUniforms>
                    }
                }
            }
        }

        logger.info("Loaded successfully: $descr")

        boundingVolumeComponentMapper[entityId].boundingVolume.apply {
            localMin.set(model.boundingVolume.min)
            localMax.set(model.boundingVolume.max)
        }
        return world.edit(entityId).run {
            when (model) {
                is AnimatedModel -> {
                    add(AnimationControllerComponent(model.animationController))
                }

                is StaticModel -> {}
            }
            ModelCacheComponent(model, allocateVertexIndexBufferSpace(descr, model)).apply {
                add(this)
                logger.info("Added modelcache component for entity $entityId")
            }
        }
    }

    override fun processSystem() {
        modelCache.values.forEach {
            when (it) {
                is AnimatedModel -> it.update(world.delta)
                is StaticModel -> {}
            }
        }
    }

    fun allocateVertexIndexBufferSpace(
        descr: ModelComponentDescription, model: Model<*>
    ): Allocation {
        val allocation = when (model) {
            is AnimatedModel -> {
                val vertexIndexBuffer = vertexIndexBufferAnimated
                val vertexIndexOffsets = vertexIndexBuffer.allocateForModel(model)
                val vertexIndexOffsetsForMeshes = model.putToBuffer(
                    vertexIndexBuffer,
                    vertexIndexBufferAnimated,
                    vertexIndexOffsets
                )

                val elements = model.animations.flatMap {
                    it.value.frames.flatMap { frame -> frame.jointMatrices.toList() }
                }
                joints.addAll(elements)
                val jointsOffset = joints.size
                Allocation.Animated(vertexIndexOffsetsForMeshes, jointsOffset)
            }

            is StaticModel -> {
                val vertexIndexBuffer = vertexIndexBufferStatic
                val vertexIndexOffsets = vertexIndexBuffer.allocateForModel(model)
                val vertexIndexOffsetsForMeshes = model.putToBuffer(
                    vertexIndexBuffer,
                    vertexIndexBufferAnimated,
                    vertexIndexOffsets
                )
                Allocation.Static(vertexIndexOffsetsForMeshes)
            }
        }
        allocations[descr] = allocation
        return allocation
    }

    private fun VertexIndexBuffer<*>.allocateForModel(model: Model<*>): VertexIndexOffsets {
        return allocate(model.uniqueVertices.size, model.indices.capacity() / Integer.BYTES)
    }

    fun Model<*>.captureIndexAndVertexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
        var currentIndexOffset = vertexIndexOffsets.indexOffset
        var currentVertexOffset = vertexIndexOffsets.vertexOffset

        val model = this

        return model.meshes.indices.map { i ->
            val mesh = model.meshes[i] as Mesh<*>
            VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
                currentIndexOffset += mesh.indexBufferValues.capacity() / Integer.BYTES
                currentVertexOffset += mesh.vertices.size
            }
        }
    }

    private fun Model<*>.putToBuffer(
        vertexIndexBuffer: VertexIndexBuffer<*>,
        vertexIndexBufferAnimated: VertexIndexBuffer<*>,
        vertexIndexOffsets: VertexIndexOffsets,
    ): List<VertexIndexOffsets> {
        val model = this
        val (targetBuffer, vertexType) = when (model) {
            is AnimatedModel -> Pair(vertexIndexBufferAnimated, AnimatedVertexStruktPacked.type)
            is StaticModel -> Pair(vertexIndexBuffer, VertexStruktPacked.type)
        }
        return synchronized(targetBuffer) {
            val vertexIndexOffsetsForMeshes = captureIndexAndVertexOffsets(vertexIndexOffsets)
            targetBuffer.vertexStructArray.addAll(
                vertexIndexOffsets.vertexOffset * vertexType.sizeInBytes,
                model.verticesPacked.byteBuffer
            )
            targetBuffer.indexBuffer.appendIndices(vertexIndexOffsets.indexOffset, model.indices)
            vertexIndexOffsetsForMeshes
        }
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic = vertexIndexBufferStatic
        currentWriteState[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated = vertexIndexBufferAnimated

        val targetJointsBuffer = currentWriteState[entitiesStateHolder.entitiesState].jointsBuffer
        targetJointsBuffer.ensureCapacityInBytes(joints.size * Matrix4fStrukt.sizeInBytes)

        with(targetJointsBuffer) {
            for ((index, joint) in joints.withIndex()) {
                byteBuffer.run {
                    this@with[index].set(joint)
                }
            }
        }
    }
}