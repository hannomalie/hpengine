package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import Vector4fStruktImpl.Companion.type
import VoxelGridImpl.Companion.type
import com.artemis.World
import de.hanno.hpengine.artemis.GiVolumeComponent
import de.hanno.hpengine.backend.Backend

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.constants.Capability.*
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_3D
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.renderer.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.Update
import de.hanno.hpengine.model.texture.Texture3D
import de.hanno.hpengine.model.texture.OpenGLTextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.Transform
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL30.GL_RED_INTEGER
import kotlin.math.max

fun OpenGLTextureManager.createGIVolumeGrids(gridSize: Int = 256): VoxelConeTracingExtension.GIVolumeGrids {
    return VoxelConeTracingExtension.GIVolumeGrids(
        getTexture3D(
            gridSize,
            VoxelConeTracingExtension.gridTextureFormatSized,
            de.hanno.hpengine.graphics.renderer.constants.MinFilter.LINEAR_MIPMAP_LINEAR,
            de.hanno.hpengine.graphics.renderer.constants.MagFilter.LINEAR,
            GL12.GL_CLAMP_TO_EDGE
        ),
        getTexture3D(
            gridSize,
            VoxelConeTracingExtension.indexGridTextureFormatSized,
            de.hanno.hpengine.graphics.renderer.constants.MinFilter.NEAREST,
            de.hanno.hpengine.graphics.renderer.constants.MagFilter.NEAREST,
            GL12.GL_CLAMP_TO_EDGE
        ),
        getTexture3D(
            gridSize,
            VoxelConeTracingExtension.gridTextureFormatSized,
            de.hanno.hpengine.graphics.renderer.constants.MinFilter.LINEAR_MIPMAP_LINEAR,
            de.hanno.hpengine.graphics.renderer.constants.MagFilter.LINEAR,
            GL12.GL_CLAMP_TO_EDGE
        ),
        getTexture3D(
            gridSize,
            VoxelConeTracingExtension.gridTextureFormatSized,
            de.hanno.hpengine.graphics.renderer.constants.MinFilter.LINEAR_MIPMAP_LINEAR,
            de.hanno.hpengine.graphics.renderer.constants.MagFilter.LINEAR,
            GL12.GL_CLAMP_TO_EDGE
        )
    )
}

class VoxelConeTracingExtension(
    val config: Config,
    val gpuContext: GpuContext,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager,
    val pointLightExtension: BvHPointLightSecondPassExtension,
    val deferredRenderingBuffer: DeferredRenderingBuffer
) : DeferredRenderExtension {

    private val lineVertices = PersistentMappedBuffer(100 * Vector4fStrukt.type.sizeInBytes, gpuContext).typed(Vector4fStrukt.type)
    val voxelGrids = renderStateManager.renderState.registerState {
        PersistentMappedBuffer(VoxelGrid.type.sizeInBytes, gpuContext).typed(VoxelGrid.type)
    }
    data class GIVolumeGrids(val grid: Texture3D,
                             val indexGrid: Texture3D,
                             val albedoGrid: Texture3D,
                             val normalGrid: Texture3D
    ) {

        val gridSize: Int = albedoGrid.dimension.width

    }

    private val voxelizerStatic = programManager.getProgram(
        config.EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
        Defines(),
        VoxelizerUniformsStatic(gpuContext)
    )

    private val voxelizerAnimated = programManager.getProgram(
        config.EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
        Defines(),
        VoxelizerUniformsStatic(gpuContext)
    )

    private val voxelConeTraceProgram = programManager.getProgram(
        config.EngineAsset("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/voxel_cone_trace_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        Uniforms.Empty
    )

    private val texture3DMipMapAlphaBlendComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_mipmap_alphablend_compute.glsl"))
    private val texture3DMipMapComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_mipmap_compute.glsl"))
    private val clearDynamicVoxelsComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_clear_dynamic_voxels_compute.glsl"))
    private val injectLightComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_inject_light_compute.glsl"))
    private val injectMultipleBounceLightComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/texture3D_inject_bounce_light_compute.glsl"))

    private var lightInjectedFramesAgo: Int = 0

    private val staticPipeline = DirectFirstPassPipeline(config, gpuContext, voxelizerStatic)
    private val animatedPipeline = DirectFirstPassPipeline(config, gpuContext, voxelizerAnimated)
    private val firstPassResult = FirstPassResult()
    private val useIndirectDrawing = false

    private var litInCycle: Long = -1
    private val entityVoxelizedInCycle = mutableMapOf<String, Long>()

    private var sceneInitiallyDrawn = false
    private var voxelizeDynamicEntites = false

    private var gridCache = mutableMapOf<Int, GIVolumeGrids>()

    override fun renderFirstPass(backend: Backend, gpuContext: GpuContext, firstPassResult: FirstPassResult, renderState: RenderState) = profiled("VCT first pass") {
        val directionalLightMoved = renderState.directionalLightHasMovedInCycle > litInCycle
        val pointlightMoved = renderState.pointLightMovedInCycle > litInCycle
        val bounces = 1

        val entitiesToVoxelize = if(!sceneInitiallyDrawn || config.debug.isForceRevoxelization) {
            renderState.renderBatchesStatic
        } else {
            renderState.renderBatchesStatic.filter { batch ->
                val entityVoxelizationCycle = entityVoxelizedInCycle[batch.entityName]
                val voxelizeBecauseItIsDynamic = voxelizeDynamicEntites && batch.update == Update.DYNAMIC
                val preventVoxelization = !batch.contributesToGi || if(voxelizeDynamicEntites) false else batch.update == Update.DYNAMIC
                if(entityVoxelizationCycle == null) {
                    !preventVoxelization
                } else {
                    batch.movedInCycle > entityVoxelizationCycle && voxelizeBecauseItIsDynamic
                }
            }
        }

        val needsRevoxelization = entitiesToVoxelize.isNotEmpty()
        voxelizeScene(renderState, entitiesToVoxelize)


        if ((config.performance.updateGiOnSceneChange || config.debug.isForceRevoxelization) && (needsRevoxelization || directionalLightMoved || pointlightMoved)) {
            lightInjectedFramesAgo = 0
        }
        val needsLightInjection = lightInjectedFramesAgo < bounces


        injectLight(renderState, bounces, needsLightInjection)
        config.debug.isForceRevoxelization = false
    }

    fun voxelizeScene(renderState: RenderState, batches: List<RenderBatch>) {
        if(batches.isEmpty()) return
        println("Voxelizing....")

        val voxelGrids = renderState[voxelGrids]
        val maxGridCount = voxelGrids.typedBuffer.size
        voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->
            profiled("Clear voxels") {
                if (config.debug.isForceRevoxelization || !sceneInitiallyDrawn) {
                    ARBClearTexture.glClearTexImage(currentVoxelGrid.grid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                    ARBClearTexture.glClearTexImage(currentVoxelGrid.indexGrid, 0, indexGridTextureFormat, GL11.GL_INT, ZERO_BUFFER_INT)
                    ARBClearTexture.glClearTexImage(currentVoxelGrid.normalGrid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                    ARBClearTexture.glClearTexImage(currentVoxelGrid.albedoGrid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                } else {
                    clearDynamicVoxelsComputeProgram.use()
                    val num_groups_xyz = Math.max(currentVoxelGrid.gridSize / 8, 1)
                    GL42.glBindImageTexture(0, currentVoxelGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                    GL42.glBindImageTexture(1, currentVoxelGrid.normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized)
                    GL42.glBindImageTexture(3, currentVoxelGrid.currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                    clearDynamicVoxelsComputeProgram.bindShaderStorageBuffer(5, voxelGrids)
                    clearDynamicVoxelsComputeProgram.setUniform("voxelGridIndex", voxelGridIndex)
                    clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
                }
            }

            profiled("Voxelization") {
                gpuContext.viewPort(0, 0, currentVoxelGrid.gridSize, currentVoxelGrid.gridSize)
                voxelizerStatic.use()
                GL42.glBindImageTexture(3, currentVoxelGrid.normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                GL42.glBindImageTexture(5, currentVoxelGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                GL42.glBindImageTexture(6, currentVoxelGrid.indexGrid, 0, true, 0, GL15.GL_WRITE_ONLY, indexGridTextureFormatSized)

                voxelizerStatic.setUniform("voxelGridIndex", voxelGridIndex)
                voxelizerStatic.setUniform("voxelGridCount", voxelGrids.typedBuffer.size)
                voxelizerStatic.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                voxelizerStatic.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                voxelizerStatic.bindShaderStorageBuffer(5, voxelGrids)
                voxelizerStatic.bindShaderStorageBuffer(7, renderState.vertexIndexBufferStatic.vertexStructArray)

                voxelizerStatic.setUniform("writeVoxels", true)
                gpuContext.depthMask = false
                gpuContext.disable(DEPTH_TEST)
                gpuContext.disable(BLEND)
                gpuContext.disable(CULL_FACE)
                GL11.glColorMask(false, false, false, false)

                renderState.vertexIndexBufferStatic.indexBuffer.bind()
                for (entity in batches) {
                    voxelizerStatic.setTextureUniforms(gpuContext, entity.material.maps)
                    renderState.vertexIndexBufferStatic.indexBuffer.draw(
                        entity.drawElementsIndirectCommand,
                        bindIndexBuffer = false,
                        primitiveType = PrimitiveType.Triangles,
                        mode = RenderingMode.Faces
                    )
                }

                if(config.debug.isDebugVoxels) {
                    GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                    mipmapGrid(currentVoxelGrid.currentVoxelSource, texture3DMipMapAlphaBlendComputeProgram, renderState)
                }
            }
        }

        for (entity in batches) {
            entityVoxelizedInCycle[entity.entityName] = renderState.cycle
        }
        sceneInitiallyDrawn = true
    }

    fun injectLight(renderState: RenderState, bounces: Int, needsLightInjection: Boolean) {
        if (needsLightInjection) {
            val voxelGrids = renderState[this.voxelGrids]
            litInCycle = renderState.cycle
            profiled("grid shading") {
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
                val maxGridCount = voxelGrids.typedBuffer.size
                voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->

                    val numGroupsXyz = max(currentVoxelGrid.gridSize / 8, 1)

                    if(lightInjectedFramesAgo == 0) {
                        with(injectLightComputeProgram) {
                            use()
                            gpuContext.bindTexture(1, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                            gpuContext.bindTexture(2, TEXTURE_3D, currentVoxelGrid.normalGrid)
                            gpuContext.bindTexture(3, TEXTURE_3D, currentVoxelGrid.indexGrid)
                            if(renderState.directionalLightState.typedBuffer.size > 0) {
                                gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId })
                            }
                            GL42.glBindImageTexture(0, currentVoxelGrid.grid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                            setUniform("pointLightCount", renderState.lightState.pointLights.size)
                            bindShaderStorageBuffer(1, renderState.materialBuffer)
                            bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                            bindShaderStorageBuffer(3, renderState.directionalLightState)
                            bindShaderStorageBuffer(4, renderState.entitiesBuffer)
                            bindShaderStorageBuffer(5, voxelGrids)
                            bindShaderStorageBuffer(6, pointLightExtension.bvh)
                            setUniform("nodeCount", pointLightExtension.nodeCount)
                            setUniform("bounces", bounces)
                            setUniform("voxelGridIndex", voxelGridIndex)
                            setUniform("voxelGridCount", voxelGrids.typedBuffer.size)

                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
                            mipmapGrid(currentVoxelGrid.grid, renderState = renderState)
                        }
                    }
//                    if(bounces > 1 && lightInjectedFramesAgo == 0) {//> 0) {
//                        with(injectMultipleBounceLightComputeProgram) {
//                            use()
//                            GL42.glBindImageTexture(0, currentVoxelGrid.grid, 0, false, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
//                            setUniform("bounces", bounces)
//                            setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo)
//                            setUniform("voxelGridIndex", voxelGridIndex)
//                            bindShaderStorageBuffer(5, renderState.get(voxelGridBufferRef).voxelGridBuffer)
//                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
//                            mipmapGrid(currentVoxelGrid.grid)
//                        }
//                    }
                }

                GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                lightInjectedFramesAgo++
            }
        }
        GL11.glColorMask(true, true, true, true)
    }

    override fun update(deltaSeconds: Float) = staticPipeline.prepare(renderStateManager.renderState.currentWriteState)

    private fun mipmapGrid(textureId: Int, renderState: RenderState) = profiled("grid mipmap") {
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        mipmapGrid(textureId, texture3DMipMapAlphaBlendComputeProgram, renderState)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
    }

    private fun mipmapGrid(texture3D: Int, shader: IComputeProgram<out Uniforms>, renderState: RenderState) {
        shader.use()
        val voxelGrids = renderState[this.voxelGrids]
        val globalGrid = voxelGrids.typedBuffer[0]
        val size = voxelGrids.typedBuffer.byteBuffer.run { globalGrid.gridSize }
        var currentSizeSource = 2 * size
        var currentMipMapLevel = 0

        while (currentSizeSource > 1) {
            currentSizeSource /= 2
            val currentSizeTarget = currentSizeSource / 2
            currentMipMapLevel++

            GL42.glBindImageTexture(0, texture3D, currentMipMapLevel - 1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized)
            GL42.glBindImageTexture(1, texture3D, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
            GL42.glBindImageTexture(3, voxelGrids.typedBuffer.byteBuffer.run { globalGrid.normalGrid }, currentMipMapLevel - 1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized)
            shader.setUniform("sourceSize", currentSizeSource)
            shader.setUniform("targetSize", currentSizeTarget)

            val num_groups_xyz = Math.max(currentSizeTarget / 8, 1)
            shader.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
        }
    }

    override fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        gpuContext.depthTest = false
        val voxelGrids = renderState[this.voxelGrids]
        profiled("VCT second pass") {

//            engine.gpuContext.blend = true
//            engine.gpuContext.blendEquation = BlendMode.FUNC_ADD
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
            val maxGridCount = voxelGrids.typedBuffer.size
            voxelGrids.typedBuffer.forEachIndexed(maxGridCount) { voxelGridIndex, currentVoxelGrid ->
                // Just for safety, if voxelgrid data is not initialized, to not freeze your pc
                if(currentVoxelGrid.scale > 0.1f) {
                    gpuContext.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
                    gpuContext.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
                    gpuContext.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                    gpuContext.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
                    gpuContext.bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                    gpuContext.bindTexture(9, TEXTURE_3D, currentVoxelGrid.grid)
                    gpuContext.bindTexture(10, TEXTURE_3D, currentVoxelGrid.indexGrid)
                    gpuContext.bindTexture(11, TEXTURE_2D, deferredRenderingBuffer.ambientOcclusionScatteringMap)
                    gpuContext.bindTexture(12, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                    gpuContext.bindTexture(13, TEXTURE_3D, currentVoxelGrid.normalGrid)

                    voxelConeTraceProgram.use()
                    val camTranslation = Vector3f()
                    voxelConeTraceProgram.setUniform("voxelGridIndex", voxelGridIndex)
                    voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.transform.getTranslation(camTranslation))
                    voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
                    voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
                    voxelConeTraceProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
                    voxelConeTraceProgram.bindShaderStorageBuffer(5, voxelGrids)
                    voxelConeTraceProgram.setUniform("voxelGridCount", voxelGrids.typedBuffer.size)
                    voxelConeTraceProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
                    voxelConeTraceProgram.setUniform("screenWidth", config.width.toFloat() / 2f)
                    voxelConeTraceProgram.setUniform("screenHeight", config.height.toFloat() / 2f)
                    voxelConeTraceProgram.setUniform("skyBoxMaterialIndex", renderState.skyBoxMaterialIndex)
                    voxelConeTraceProgram.setUniform("debugVoxels", config.debug.isDebugVoxels)
                    gpuContext.fullscreenBuffer.draw()
                    //        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
                    //        if(entityOrDirectionalLightHasMoved)
                    //        {
                    //            if only second bounce, clear current target texture
                    //            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
                    //        }
                }
            }
        }
        gpuContext.depthTest = true
    }

    override fun extract(renderState: RenderState, world: World) {
        extract(renderState)
    }
    fun extract(renderState: RenderState) {
        val list = (
            renderState.componentExtracts[GiVolumeComponent::class.java] ?: emptyList<GiVolumeComponent>()
        ) as List<GiVolumeComponent>
        if(list.isEmpty()) return

        val targetSize = list.size
        renderState[voxelGrids].resize(targetSize)
//        for (index in renderState[voxelGrids].indices) {
//            val target = renderState[voxelGrids][index]
//            val source = list[index]
//             TODO: Use volume grid cache somehow
//            val giVolumeGrids = source.giVolumeGrids
//            target.grid = giVolumeGrids.grid.id
//            target.gridHandle = giVolumeGrids.grid.handle
//            target.indexGrid = giVolumeGrids.indexGrid.id
//            target.indexGridHandle = giVolumeGrids.indexGrid.handle
//            target.albedoGrid = giVolumeGrids.albedoGrid.id
//            target.albedoGridHandle = giVolumeGrids.albedoGrid.handle
//            target.normalGrid = giVolumeGrids.normalGrid.id
//            target.normalGridHandle = giVolumeGrids.normalGrid.handle
//            target.gridSize = source.giVolumeGrids.gridSize
//            target.position.set(source.entity.transform.position)
//            target.scale = source.scale
//            target.projectionMatrix.set(source.orthoCam.projectionMatrix)
//        }
    }

    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        val grids = renderState[voxelGrids]

//             TODO: Reimplement
//        val linePoints = mutableListOf<Vector3fc>().apply {
//            for(gridIndex in grids.indices) {
//                val grid = grids[gridIndex]
//                addAABBLines(
//                    grid.position.toJoml().sub(Vector3f(grid.gridSizeHalfScaled.toFloat())),
//                    grid.position.toJoml().add(Vector3f(grid.gridSizeHalfScaled.toFloat()))
//                )
//            }
//        }
//        deferredRenderingBuffer.finalBuffer.use(gpuContext, false)
//        gpuContext.blend = false
//
//        drawLines(renderStateManager, programManager, lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
    }

    companion object {
        val gridTextureFormat = GL11.GL_RGBA//GL11.GL_R;
        val gridTextureFormatSized = GL11.GL_RGBA8//GL30.GL_R32UI;
        val indexGridTextureFormat = GL_RED_INTEGER//GL30.GL_R32UI;
        val indexGridTextureFormatSized = GL30.GL_R16I//GL30.GL_R32UI;
    }
}

class VoxelizerUniformsStatic(val gpuContext: GpuContext) : StaticFirstPassUniforms(gpuContext) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, PersistentMappedBuffer(VoxelGrid.type.sizeInBytes, gpuContext).typed(VoxelGrid.type))
    val writeVoxels by BooleanType(true)
}
class VoxelizerUniformsAnimated(val gpuContext: GpuContext) : AnimatedFirstPassUniforms(gpuContext) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, PersistentMappedBuffer(VoxelGrid.type.sizeInBytes, gpuContext).typed(VoxelGrid.type))
    val writeVoxels by BooleanType(true)
}

var ZERO_BUFFER = BufferUtils.createFloatBuffer(4).apply {
    put(0f)
    put(0f)
    put(0f)
    put(0f)
    rewind()
}
var ZERO_BUFFER_INT = BufferUtils.createIntBuffer(4).apply {
    put(0)
    put(0)
    put(0)
    put(0)
    rewind()
}
var BLUE_BUFFER = BufferUtils.createFloatBuffer(4).apply {
    put(0f)
    put(0f)
    put(1f)
    put(0f)
    rewind()
}
val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
    Transform().get(this)
}