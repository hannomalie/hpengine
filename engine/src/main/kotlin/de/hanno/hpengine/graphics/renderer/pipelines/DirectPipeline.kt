package de.hanno.hpengine.graphics.renderer.pipelines


import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
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
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.scene.GeometryBuffer
import de.hanno.hpengine.toCount
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.FrustumIntersection

open class DirectPipeline(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val program: Program<out DefaultUniforms>,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val fallbackTexture: Texture? = null,
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

                drawCustomProgramBatches(camera, entitiesState, renderState, geometryBuffer, mode)
                drawDefaultProgramBatches(camera, entitiesState, renderState, geometryBuffer, mode)
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
            when(uniforms) {
                is AnimatedDefaultUniforms -> uniforms.vertices = geometryBuffer.vertexStructArray
                is StaticDefaultUniforms -> uniforms.vertices = geometryBuffer.vertexStructArray
            }

            val batchesWithPipelineProgram =
                renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }
            logger.trace("Render ${batchesWithPipelineProgram.size} default pipeline program batches")
            for (batch in batchesWithPipelineProgram) {
                depthMask = batch.material.writesDepth
                cullFace = batch.material.cullBackFaces
                depthTest = batch.material.depthTest
                program.setTextureUniforms(graphicsApi, batch.material.maps, fallbackTexture)
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

                cullFace = batch.material.cullBackFaces
                depthTest = batch.material.depthTest
                depthMask = batch.material.writesDepth

                using(program) { uniforms ->
                    uniforms.setCommonUniformValues(
                        renderState,
                        entitiesState,
                        camera,
                        config,
                        materialSystem,
                        entityBuffer
                    )
                }
                program.setTextureUniforms(graphicsApi, batch.material.maps, fallbackTexture)

                program.bind()
                geometryBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = program.primitiveType,
                    mode = mode,
                    bindIndexBuffer = false,
                )
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
            joints = entitiesState.jointsBuffer
            vertices = entitiesState.geometryBufferAnimated.vertexStructArray
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
