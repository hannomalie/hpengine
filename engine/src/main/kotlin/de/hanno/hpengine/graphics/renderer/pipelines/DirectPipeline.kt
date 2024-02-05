package de.hanno.hpengine.graphics.renderer.pipelines


import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.constants.RenderingMode.Fill
import de.hanno.hpengine.graphics.constants.RenderingMode.Lines
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.forward.AnimatedFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.forward.FirstPassUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.scene.VertexIndexBuffer
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.FrustumIntersection

open class DirectPipeline(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val program: Program<out FirstPassUniforms>,
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
    private var verticesCount = 0
    private var entitiesCount = 0
    private var renderBatches = emptyList<RenderBatch>()

    fun prepare(renderState: RenderState) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        renderBatches = renderState.extractRenderBatches()
        logger.trace("Prepared ${renderBatches.size} batches")
    }

    open fun RenderState.extractRenderBatches(): List<RenderBatch> = this[defaultBatchesSystem.renderBatchesStatic].filterNot {
        it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
    }
    open fun RenderState.selectVertexIndexBuffer(): VertexIndexBuffer<*> = this[entitiesStateHolder.entitiesState].vertexIndexBufferStatic

    fun draw(renderState: RenderState): Unit = graphicsApi.run {
        profiled("Actual draw entities") {

            val mode = if (config.debug.isDrawLines) Lines else Fill
            val vertexIndexBuffer = renderState.selectVertexIndexBuffer()

            vertexIndexBuffer.indexBuffer.bind()

            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            val camera = renderState[primaryCameraStateHolder.camera]

            drawCustomProgramBatches(camera, entitiesState, renderState, vertexIndexBuffer, mode)
            drawDefaultProgramBatches(camera, entitiesState, renderState, vertexIndexBuffer, mode)
        }
    }

    private fun drawDefaultProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        vertexIndexBuffer: VertexIndexBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        using(program) { uniforms: FirstPassUniforms ->
            uniforms.setCommonUniformValues(renderState, entitiesState, camera)

            val batchesWithPipelineProgram = renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }
            logger.trace("Render ${batchesWithPipelineProgram.size} default pipeline program batches")
            for (batch in batchesWithPipelineProgram) {
                depthMask = batch.material.writesDepth
                cullFace = batch.material.cullBackFaces
                depthTest = batch.material.depthTest
                setTextureUniforms(program, graphicsApi, batch.material.maps, fallbackTexture)
                program.uniforms.entityIndex = batch.entityBufferIndex
                program.bind()
                vertexIndexBuffer.indexBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = PrimitiveType.Triangles,
                    mode = mode,
                    bindIndexBuffer = false,
                )
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }
        }
    }

    private fun drawCustomProgramBatches(
        camera: Camera,
        entitiesState: EntitiesState,
        renderState: RenderState,
        vertexIndexBuffer: VertexIndexBuffer<*>,
        mode: RenderingMode
    ) = graphicsApi.run {
        val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }

        logger.trace("Render ${batchesWithOwnProgram.size} custom program batches")
        for (groupedBatches in batchesWithOwnProgram) {
            for (batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
                val program = batch.program!! as Program<FirstPassUniforms> // TODO: This is not safe

                cullFace = batch.material.cullBackFaces
                depthTest = batch.material.depthTest
                depthMask = batch.material.writesDepth

                using(program) { uniforms ->
                    uniforms.setCommonUniformValues(renderState, entitiesState, camera)
                }
                setTextureUniforms(program, graphicsApi, batch.material.maps, fallbackTexture)

                program.bind()
                vertexIndexBuffer.indexBuffer.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = program.primitiveType,
                    mode = mode,
                    bindIndexBuffer = false,
                )
                verticesCount += batch.vertexCount
                entitiesCount += 1
            }
        }
    }

    private fun FirstPassUniforms.setCommonUniformValues(
        renderState: RenderState,
        entitiesState: EntitiesState,
        camera: Camera
    ) {
        materials = renderState[materialSystem.materialBuffer]
        entities = renderState[entityBuffer.entitiesBuffer]
        indirect = false
        when (this) {
            is StaticFirstPassUniforms -> vertices =
                entitiesState.vertexIndexBufferStatic.vertexStructArray

            is AnimatedFirstPassUniforms -> {
                joints = entitiesState.jointsBuffer
                vertices = entitiesState.vertexIndexBufferAnimated.vertexStructArray
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

}

val Program<*>.primitiveType get() = if (shaders.firstIsInstanceOrNull<TesselationControlShader>() != null) {
    PrimitiveType.Patches
} else {
    PrimitiveType.Triangles
}


fun setTextureUniforms(
    program: Program<*>,
    graphicsApi: GraphicsApi,
    maps: Map<Material.MAP, Texture>,
    diffuseFallbackTexture: Texture? = null
) = graphicsApi.run {
    for (mapEnumEntry in Material.MAP.entries) {
        if (maps.contains(mapEnumEntry)) {
            val map = maps[mapEnumEntry]!!
            if (map.id > 0) {
                val isDiffuse = mapEnumEntry == Material.MAP.DIFFUSE

                when(map.uploadState) {
                    UploadState.Uploaded -> {
                        bindTexture(mapEnumEntry.textureSlot, map)
                        program.setUniform(mapEnumEntry.uniformKey, true)
                        if(isDiffuse) {
                            program.setUniform("diffuseMipBias", 0)
                        }
                    }
                    UploadState.NotUploaded -> {
                        if(isDiffuse) {
                            if(diffuseFallbackTexture != null) {
                                bindTexture(mapEnumEntry.textureSlot, diffuseFallbackTexture)
                                program.setUniform(mapEnumEntry.uniformKey, true)
                                program.setUniform("diffuseMipBias", 0)
                            } else {
                                program.setUniform(mapEnumEntry.uniformKey, false)
                                program.setUniform("diffuseMipBias", 0)
                            }
                        } else {
                            program.setUniform(mapEnumEntry.uniformKey, false)
                        }
                    }
                    is UploadState.Uploading -> {
                        if(isDiffuse) {
                            bindTexture(mapEnumEntry.textureSlot, map)
                            program.setUniform(mapEnumEntry.uniformKey, true)
                            program.setUniform(
                                "diffuseMipBias",
                                (map.uploadState as UploadState.Uploading).maxMipMapLoaded
                            )
                        } else {
                            program.setUniform(mapEnumEntry.uniformKey, false)
                        }
                    }
                }
            }
        } else {
            program.setUniform(mapEnumEntry.uniformKey, false)
        }
    }
}

fun RenderBatch.isCulled(cullCam: Camera): Boolean {
    if(neverCull) return false
    if (!isVisible) return true

    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera =
        meshIsInFrustum || drawElementsIndirectCommand.instanceCount > 1 // TODO: Better culling for instances

    return !visibleForCamera
}

val RenderBatch.isForwardRendered: Boolean get() = material.transparencyType.needsForwardRendering
