package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.utils.IntBag
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.artemis.forEach
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
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
import de.hanno.hpengine.scene.*
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.ModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.toCount
import org.apache.logging.log4j.LogManager
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

    private val geometryBufferAnimated: GeometryBuffer<AnimatedVertexStruktPacked> = entitiesStateHolder.geometryBufferAnimated
    private val geometryBufferStatic: GeometryBuffer<VertexStruktPacked> = entitiesStateHolder.geometryBufferStatic

    lateinit var modelComponentMapper: ComponentMapper<ModelComponent>
    lateinit var instanceComponentMapper: ComponentMapper<InstanceComponent>
    lateinit var boundingVolumeComponentMapper: ComponentMapper<BoundingVolumeComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

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
            val allocation = allocateGeometryBufferSpace(model).apply {
                allocations[descr] = this
            }
            ModelCacheComponent(model, allocation).apply {
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

    fun allocateGeometryBufferSpace(
        model: Model<*>
    ): Allocation = when (model) {
        is AnimatedModel -> {
            val geometryOffsetsForMeshes = model.putToBuffer(geometryBufferAnimated)

            val elements = model.animations.flatMap {
                it.value.frames.flatMap { frame -> frame.jointMatrices.toList() }
            }
            val jointsOffset = joints.size
            joints.addAll(elements)
            Allocation.Animated(geometryOffsetsForMeshes, jointsOffset)
        }

        is StaticModel -> {
            val geometryOffsetsForMeshes = model.putToBuffer(geometryBufferStatic)
            Allocation.Static(geometryOffsetsForMeshes)
        }
    }

    override fun extract(currentWriteState: RenderState) {
        val targetJointsBuffer = currentWriteState[entitiesStateHolder.entitiesState].jointsBuffer
        targetJointsBuffer.ensureCapacityInBytes(joints.size.toCount() * SizeInBytes(Matrix4fStrukt.sizeInBytes))

        with(targetJointsBuffer) {
            for ((index, joint) in joints.withIndex()) {
                byteBuffer.run {
                    this@with[index].set(joint)
                }
            }
        }
    }
}
