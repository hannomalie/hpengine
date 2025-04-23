package de.hanno.hpengine.graphics.renderer.pipelines


import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BlendMode
import de.hanno.hpengine.graphics.constants.CullMode
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.constants.RenderingMode.Fill
import de.hanno.hpengine.graphics.constants.RenderingMode.Lines
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.forward.AnimatedDefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.DefaultUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.TesselationControlShader
import de.hanno.hpengine.graphics.shader.using
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.StaticHandle
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureHandle
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.scene.GeometryBuffer
import de.hanno.hpengine.toCount
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.toSmartList
import org.joml.FrustumIntersection
import kotlin.math.min

open class DirectPipeline(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val program: Program<out DefaultUniforms>,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val fallbackTexture: StaticHandle<Texture2D>? = null,
    protected val shouldBeSkipped: RenderBatch.(Camera) -> Boolean = { cullCam: Camera ->
        isCulled(cullCam) || isForwardRendered
    }
) {
    private val logger = LogManager.getLogger(this.javaClass)
    private var verticesCount = 0.toCount()
    private var entitiesCount = 0.toCount()
    private var renderBatches = emptyList<RenderBatch>()
    val preparedBatches: List<RenderBatch> get() = renderBatches

    fun prepare(renderState: RenderState, camera: Camera = renderState[primaryCameraStateHolder.camera]) {
        if (config.debug.freezeCulling) return
        verticesCount = 0.toCount()
        entitiesCount = 0.toCount()

        renderBatches = renderState.extractRenderBatches(camera)
        logger.trace("Prepared ${renderBatches.size} batches")
    }

    open fun RenderState.extractRenderBatches(camera: Camera): List<RenderBatch> =
        this[defaultBatchesSystem.renderBatchesStatic].filterNot {
            it.shouldBeSkipped(camera)
        }

    open fun RenderState.selectGeometryBuffer(): GeometryBuffer<*> =
        this[entitiesStateHolder.entitiesState].geometryBufferStatic

    fun draw(renderState: RenderState, camera: Camera = renderState[primaryCameraStateHolder.camera]): Unit =
        graphicsApi.run {
            profiled("Actual draw entities") {

                val mode = if (config.debug.isDrawLines) Lines else Fill
                val geometryBuffer = renderState.selectGeometryBuffer()

                geometryBuffer.bind()

                val entitiesState = renderState[entitiesStateHolder.entitiesState]

                blend = false
                depthMask = true
                depthTest = false
                cullFace = false

                drawDefaultProgramBatches(camera, entitiesState, renderState, geometryBuffer, mode)
                drawCustomProgramBatches(camera, entitiesState, renderState, geometryBuffer, mode)
            }
        }

    private fun drawDefaultProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        geometryBuffer: GeometryBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        using(program) { uniforms: DefaultUniforms ->
            uniforms.setCommonUniformValues(renderState, entitiesState, camera, config, materialSystem, entityBuffer)

            val batchesWithPipelineProgram =
                renderBatches.filter { !it.hasOwnProgram  && it.isVisible }.sortedBy { it.material.renderPriority }
            logger.trace("Render ${batchesWithPipelineProgram.size} default pipeline program batches")

            val (opaqueBatches, transparentBatches) = batchesWithPipelineProgram.partition { it.material.transparency == 0f }

            fun List<RenderBatch>.actuallyRender(preventDepthWrite: Boolean = false) {
                for (batch in this) {
                    depthMask = if(preventDepthWrite) false else batch.material.writesDepth
                    depthTest = batch.material.depthTest
                    cullFace = batch.material.cullingEnabled
                    cullMode = if(batch.material.cullFrontFaces) CullMode.FRONT else CullMode.BACK
                    program.setTextureUniforms(graphicsApi, fallbackTexture, batch.material)
                    program.uniforms.entityIndex = batch.entityBufferIndex
                    program.bind()
                    geometryBuffer.draw(
                        batch.drawElementsIndirectCommand,
                        primitiveType = PrimitiveType.Triangles,
                        mode = mode,
                        bindIndexBuffer = false,
                    )
                    verticesCount += batch.vertexCount
                    entitiesCount += 1.toCount()
                }
            }
            opaqueBatches.actuallyRender()
            blend = true
            depthMask = false
            blendEquation = BlendMode.FUNC_ADD
            blendFunc(BlendMode.Factor.SRC_ALPHA, BlendMode.Factor.ONE_MINUS_SRC_ALPHA)
            transparentBatches.actuallyRender(preventDepthWrite = true)
            blend = false

            val distancesForHandle = mutableMapOf<TextureHandle<*>, Float>()
            (renderState[defaultBatchesSystem.renderBatchesStatic] + renderState[defaultBatchesSystem.renderBatchesAnimated]).forEach { batch ->
                batch.material.maps.forEach { map ->
                    distancesForHandle[map.value] = if(distancesForHandle.contains(map.value)) {
                        min(distancesForHandle[map.value]!!, batch.closestDistance)
                    } else {
                        batch.closestDistance
                    }
                }
            }
            distancesForHandle.forEach {
                setHandleUsageDistance(it.key, it.value)
            }
        }
    }

    private fun drawCustomProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        geometryBuffer: GeometryBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        val batchesWithOwnProgram: Map<Material, List<RenderBatch>> =
            renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }

        logger.trace("Render ${batchesWithOwnProgram.size} custom program batches")
        for (groupedBatches in batchesWithOwnProgram) {
            for (batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
                val program = batch.program!! as Program<DefaultUniforms> // TODO: This is not safe

                cullFace = batch.material.cullingEnabled
                cullMode = if(batch.material.cullFrontFaces) CullMode.FRONT else CullMode.BACK
                depthTest = batch.material.depthTest
                depthMask = batch.material.writesDepth

                blend = batch.material.transparency != 0f
                blendEquation = BlendMode.FUNC_ADD
                blendFunc(BlendMode.Factor.SRC_ALPHA, BlendMode.Factor.ONE_MINUS_SRC_ALPHA)


                using(program) { uniforms ->
                    uniforms.setCommonUniformValues(renderState, entitiesState, camera, config, materialSystem, entityBuffer)
                }
                program.setTextureUniforms(graphicsApi, fallbackTexture, batch.material)
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                geometryBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = program.primitiveType,
                    mode = mode,
                    bindIndexBuffer = false,
                )
                blend = false

                verticesCount += batch.vertexCount
                entitiesCount += 1.toCount()
            }
        }
    }
}

val Program<*>.primitiveType
    get() = if (shaders.firstIsInstanceOrNull<TesselationControlShader>() != null) {
        PrimitiveType.Patches
    } else {
        PrimitiveType.Triangles
    }


fun DefaultUniforms.setCommonUniformValues(
    renderState: RenderState,
    entitiesState: EntitiesState,
    camera: Camera,
    config: Config,
    materialSystem: MaterialSystem,
    entityBuffer: EntityBuffer,
) {
    materials = renderState[materialSystem.materialBuffer]
    entities = renderState[entityBuffer.entitiesBuffer]
    indirect = false
    when (this) {
        is StaticDefaultUniforms -> vertices = entitiesState.geometryBufferStatic.vertexStructArray
        is AnimatedDefaultUniforms -> {
            vertices = entitiesState.geometryBufferAnimated.vertexStructArray
            joints = entitiesState.jointsBuffer
        }
    }
    useRainEffect = config.effects.rainEffect != 0.0f
    rainEffect = config.effects.rainEffect
    viewMatrix = camera.viewMatrixBuffer
    lastViewMatrix = camera.viewMatrixBuffer
    projectionMatrix = camera.projectionMatrixBuffer
    viewProjectionMatrix = camera.viewProjectionMatrixBuffer

    eyePosition = camera.getPosition()
    near = camera.near
    far = camera.far
    time = renderState.time.toInt()
    useParallax = config.quality.isUseParallax
    useSteepParallax = config.quality.isUseSteepParallax
    entityBaseIndex = 0
    indirect = false
}

fun RenderBatch.isCulled(cullCam: Camera): Boolean {
    if (neverCull) return false
    if (!isVisible) return true

    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera =
        meshIsInFrustum || drawElementsIndirectCommand.instanceCount > 1.toCount() // TODO: Better culling for instances

    return !visibleForCamera
}

val RenderBatch.isForwardRendered: Boolean get() = material.transparencyType.needsForwardRendering