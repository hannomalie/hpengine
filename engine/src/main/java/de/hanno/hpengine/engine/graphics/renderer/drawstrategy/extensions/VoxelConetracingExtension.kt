package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.BLEND
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_3D
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.AnimatedFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.BooleanType
import de.hanno.hpengine.engine.graphics.shader.ComputeProgram
import de.hanno.hpengine.engine.graphics.shader.IntType
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.SSBO
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.texture.Texture3D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBClearTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_RED_INTEGER
import org.lwjgl.opengl.GL42
import kotlin.math.max

fun TextureManager.createGIVolumeGrids(gridSize: Int = 256): VoxelConeTracingExtension.GIVolumeGrids {
    return VoxelConeTracingExtension.GIVolumeGrids(
            getTexture3D(gridSize, VoxelConeTracingExtension.gridTextureFormatSized,
                    MinFilter.LINEAR_MIPMAP_LINEAR,
                    MagFilter.LINEAR,
                    GL12.GL_CLAMP_TO_EDGE),
            getTexture3D(gridSize, VoxelConeTracingExtension.indexGridTextureFormatSized,
                    MinFilter.NEAREST,
                    MagFilter.NEAREST,
                    GL12.GL_CLAMP_TO_EDGE),
            getTexture3D(gridSize, VoxelConeTracingExtension.gridTextureFormatSized,
                    MinFilter.LINEAR_MIPMAP_LINEAR,
                    MagFilter.LINEAR,
                    GL12.GL_CLAMP_TO_EDGE),
            getTexture3D(gridSize, VoxelConeTracingExtension.gridTextureFormatSized,
                    MinFilter.LINEAR_MIPMAP_LINEAR,
                    MagFilter.LINEAR,
                    GL12.GL_CLAMP_TO_EDGE)
    )
}

class VoxelConeTracingExtension(
        private val engineContext: EngineContext,
        val pointLightExtension: BvHPointLightSecondPassExtension) : RenderExtension<OpenGl> {

    private val lineVertices = PersistentMappedStructBuffer(100, engineContext.gpuContext, { HpVector4f() })
    val voxelGrids = engineContext.renderStateManager.renderState.registerState {
        PersistentMappedStructBuffer(0, engineContext.gpuContext, { VoxelGrid() })
    }
    data class GIVolumeGrids(val grid: Texture3D,
                             val indexGrid: Texture3D,
                             val albedoGrid: Texture3D,
                             val normalGrid: Texture3D) {

        val gridSize: Int = albedoGrid.dimension.width

    }

    private val voxelizerStatic = engineContext.run {
        programManager.getProgram(
                EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
                EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
                Defines(),
                VoxelizerUniformsStatic(engineContext.gpuContext)
        )
    }

    private val voxelizerAnimated = engineContext.run {
        programManager.getProgram(
                EngineAsset("shaders/voxelize_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/voxelize_fragment.glsl").toCodeSource(),
                EngineAsset("shaders/voxelize_geometry.glsl").toCodeSource(),
                Defines(),
                VoxelizerUniformsStatic(engineContext.gpuContext)
        )
    }

    private val voxelConeTraceProgram: Program<Uniforms> = engineContext.run {
        programManager.getProgram(
                EngineAsset("shaders/passthrough_vertex.glsl").toCodeSource(),
                EngineAsset("shaders/voxel_cone_trace_fragment.glsl").toCodeSource(),
                null,
                Defines(),
                Uniforms.Empty
        )
    }

    private val texture3DMipMapAlphaBlendComputeProgram: ComputeProgram = engineContext.run { programManager.getComputeProgram(EngineAsset("shaders/texture3D_mipmap_alphablend_compute.glsl")) }
    private val texture3DMipMapComputeProgram: ComputeProgram = engineContext.run { programManager.getComputeProgram(EngineAsset("shaders/texture3D_mipmap_compute.glsl")) }
    private val clearDynamicVoxelsComputeProgram: ComputeProgram = engineContext.run { programManager.getComputeProgram(EngineAsset("shaders/texture3D_clear_dynamic_voxels_compute.glsl")) }
    private val injectLightComputeProgram: ComputeProgram = engineContext.run { programManager.getComputeProgram(EngineAsset("shaders/texture3D_inject_light_compute.glsl")) }
    private val injectMultipleBounceLightComputeProgram: ComputeProgram = engineContext.run { programManager.getComputeProgram(EngineAsset("shaders/texture3D_inject_bounce_light_compute.glsl")) }

    private var lightInjectedFramesAgo: Int = 0

    private val pipeline = DirectPipeline(engineContext)
    private val firstPassResult = FirstPassResult()
    private val useIndirectDrawing = false

    private var litInCycle: Long = -1
    private val entityVoxelizedInCycle = mutableMapOf<String, Long>()

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) = profiled("VCT first pass") {
        val directionalLightMoved = renderState.directionalLightHasMovedInCycle > litInCycle
        val pointlightMoved = renderState.pointLightMovedInCycle > litInCycle
        val bounces = 1

        val entitiesToVoxelize = if(!renderState.sceneInitiallyDrawn || engineContext.config.debug.isForceRevoxelization) {
            renderState.renderBatchesStatic
        } else {
            renderState.renderBatchesStatic.filter { batch ->
                val entityVoxelizationCycle = entityVoxelizedInCycle[batch.entityName]
                entityVoxelizationCycle == null || (batch.movedInCycle > entityVoxelizationCycle && batch.update == Update.DYNAMIC)
            }
        }

        val needsRevoxelization = entitiesToVoxelize.isNotEmpty()
        voxelizeScene(renderState, entitiesToVoxelize)


        if ((engineContext.config.performance.updateGiOnSceneChange || engineContext.config.debug.isForceRevoxelization) && (needsRevoxelization || directionalLightMoved || pointlightMoved)) {
            lightInjectedFramesAgo = 0
        }
        val needsLightInjection = lightInjectedFramesAgo < bounces


        injectLight(renderState, bounces, needsLightInjection)
        engineContext.config.debug.isForceRevoxelization = false
    }

    fun voxelizeScene(renderState: RenderState, batches: List<RenderBatch>) {
        if(batches.isEmpty()) return
        println("Voxelizing....")

        val voxelGrids = renderState[voxelGrids]
        val maxGridCount = voxelGrids.size
        for(voxelGridIndex in 0 until maxGridCount) {
            val currentVoxelGrid = voxelGrids[voxelGridIndex]
            profiled("Clear voxels") {
                if (engineContext.config.debug.isForceRevoxelization || !renderState.sceneInitiallyDrawn) {
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
                engineContext.gpuContext.viewPort(0, 0, currentVoxelGrid.gridSize, currentVoxelGrid.gridSize)
                voxelizerStatic.use()
                GL42.glBindImageTexture(3, currentVoxelGrid.normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                GL42.glBindImageTexture(5, currentVoxelGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                GL42.glBindImageTexture(6, currentVoxelGrid.indexGrid, 0, true, 0, GL15.GL_WRITE_ONLY, indexGridTextureFormatSized)

                voxelizerStatic.setUniform("voxelGridIndex", voxelGridIndex)
                voxelizerStatic.setUniform("voxelGridCount", voxelGrids.size)
                voxelizerStatic.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                voxelizerStatic.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                voxelizerStatic.bindShaderStorageBuffer(5, voxelGrids)
                voxelizerStatic.bindShaderStorageBuffer(7, renderState.vertexIndexBufferStatic.vertexStructArray)

                voxelizerStatic.setUniform("writeVoxels", true)
                engineContext.gpuContext.depthMask = false
                engineContext.gpuContext.disable(DEPTH_TEST)
                engineContext.gpuContext.disable(BLEND)
                engineContext.gpuContext.disable(CULL_FACE)
                GL11.glColorMask(false, false, false, false)

                if (useIndirectDrawing && engineContext.config.performance.isIndirectRendering) {
                    firstPassResult.reset()
                    pipeline.draw(renderState, voxelizerStatic as Program<StaticFirstPassUniforms>, voxelizerAnimated as Program<AnimatedFirstPassUniforms>, firstPassResult)
                } else {
                    renderState.vertexIndexBufferStatic.indexBuffer.bind()
                    for (entity in batches) {
                        voxelizerStatic.setTextureUniforms(entity.materialInfo.maps)
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(entity, voxelizerStatic, bindIndexBuffer = false)
                        entityVoxelizedInCycle[entity.entityName] = renderState.cycle
                    }
                }

                if(engineContext.config.debug.isDebugVoxels) {
                    GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                    mipmapGrid(currentVoxelGrid.currentVoxelSource, texture3DMipMapAlphaBlendComputeProgram, renderState)
                }
            }
        }
    }

    fun injectLight(renderState: RenderState, bounces: Int, needsLightInjection: Boolean) {
        if (needsLightInjection) {
            val voxelGrids = renderState[this.voxelGrids]
            litInCycle = renderState.cycle
            profiled("grid shading") {
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
                val maxGridCount = voxelGrids.size
                for(voxelGridIndex in 0 until maxGridCount) {
                    val currentVoxelGrid = voxelGrids[voxelGridIndex]

                    val numGroupsXyz = max(currentVoxelGrid.gridSize / 8, 1)

                    if(lightInjectedFramesAgo == 0) {
                        with(injectLightComputeProgram) {
                            use()
                            engineContext.gpuContext.bindTexture(1, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                            engineContext.gpuContext.bindTexture(2, TEXTURE_3D, currentVoxelGrid.normalGrid)
                            engineContext.gpuContext.bindTexture(3, TEXTURE_3D, currentVoxelGrid.indexGrid)
                            if(renderState.directionalLightState.size > 0) {
                                engineContext.gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
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
                            setUniform("voxelGridCount", voxelGrids.size)

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

    override suspend fun update(scene: Scene, deltaSeconds: Float) = this@VoxelConeTracingExtension.pipeline.prepare(this@VoxelConeTracingExtension.engineContext.renderStateManager.renderState.currentWriteState)

    private fun mipmapGrid(textureId: Int, renderState: RenderState) = profiled("grid mipmap") {
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        mipmapGrid(textureId, texture3DMipMapAlphaBlendComputeProgram, renderState)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
    }

    private fun mipmapGrid(texture3D: Int, shader: ComputeProgram, renderState: RenderState) {
        shader.use()
        val voxelGrids = renderState[this.voxelGrids]
        val globalGrid = voxelGrids[0]
        val size = globalGrid.gridSize
        var currentSizeSource = 2 * size
        var currentMipMapLevel = 0

        while (currentSizeSource > 1) {
            currentSizeSource /= 2
            val currentSizeTarget = currentSizeSource / 2
            currentMipMapLevel++

            GL42.glBindImageTexture(0, texture3D, currentMipMapLevel - 1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized)
            GL42.glBindImageTexture(1, texture3D, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
            GL42.glBindImageTexture(3, globalGrid.normalGrid, currentMipMapLevel - 1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized)
            shader.setUniform("sourceSize", currentSizeSource)
            shader.setUniform("targetSize", currentSizeTarget)

            val num_groups_xyz = Math.max(currentSizeTarget / 8, 1)
            shader.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
        }
    }

    override fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        engineContext.gpuContext.depthTest = false
        val voxelGrids = renderState[this.voxelGrids]
        profiled("VCT second pass") {

//            engine.gpuContext.blend = true
//            engine.gpuContext.blendEquation = BlendMode.FUNC_ADD
//                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
            val maxGridCount = voxelGrids.size
            for(voxelGridIndex in 0 until maxGridCount) {
                val currentVoxelGrid = voxelGrids[voxelGridIndex]
                if(currentVoxelGrid.scale < 0.1f) continue // Just for safety, if voxelgrid data is not initialized, to not freeze your pc

                engineContext.gpuContext.bindTexture(0, TEXTURE_2D, engineContext.deferredRenderingBuffer.positionMap)
                engineContext.gpuContext.bindTexture(1, TEXTURE_2D, engineContext.deferredRenderingBuffer.normalMap)
                engineContext.gpuContext.bindTexture(2, TEXTURE_2D, engineContext.deferredRenderingBuffer.colorReflectivenessMap)
                engineContext.gpuContext.bindTexture(3, TEXTURE_2D, engineContext.deferredRenderingBuffer.motionMap)
                engineContext.gpuContext.bindTexture(7, TEXTURE_2D, engineContext.deferredRenderingBuffer.visibilityMap)
                engineContext.gpuContext.bindTexture(9, TEXTURE_3D, currentVoxelGrid.grid)
                engineContext.gpuContext.bindTexture(10, TEXTURE_3D, currentVoxelGrid.indexGrid)
                engineContext.gpuContext.bindTexture(11, TEXTURE_2D, engineContext.deferredRenderingBuffer.ambientOcclusionScatteringMap)
                engineContext.gpuContext.bindTexture(12, TEXTURE_3D, currentVoxelGrid.albedoGrid)
                engineContext.gpuContext.bindTexture(13, TEXTURE_3D, currentVoxelGrid.normalGrid)

                voxelConeTraceProgram.use()
                val camTranslation = Vector3f()
                voxelConeTraceProgram.setUniform("voxelGridIndex", voxelGridIndex)
                voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.entity.transform.getTranslation(camTranslation))
                voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
                voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
                voxelConeTraceProgram.bindShaderStorageBuffer(0, engineContext.deferredRenderingBuffer.exposureBuffer)
                voxelConeTraceProgram.bindShaderStorageBuffer(5, voxelGrids)
                voxelConeTraceProgram.setUniform("voxelGridCount", voxelGrids.size)
                voxelConeTraceProgram.setUniform("useAmbientOcclusion", engineContext.config.quality.isUseAmbientOcclusion)
                voxelConeTraceProgram.setUniform("screenWidth", engineContext.config.width.toFloat() / 2f)
                voxelConeTraceProgram.setUniform("screenHeight", engineContext.config.height.toFloat() / 2f)
                voxelConeTraceProgram.setUniform("skyBoxMaterialIndex", renderState.skyBoxMaterialIndex)
                voxelConeTraceProgram.setUniform("debugVoxels", engineContext.config.debug.isDebugVoxels)
                engineContext.gpuContext.fullscreenBuffer.draw()
                //        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
                //        if(entityOrDirectionalLightHasMoved)
                //        {
                //            if only second bounce, clear current target texture
                //            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
                //        }
            }
        }
        engineContext.gpuContext.depthTest = true
    }

    fun extract(renderState: RenderState, list: List<GIVolumeComponent>) {
        val targetSize = list.size
        renderState[voxelGrids].resize(targetSize)
        for (index in renderState[voxelGrids].indices) {
            val target = renderState[voxelGrids][index]
            val source = list[index]
            val giVolumeGrids = source.giVolumeGrids
            target.grid = giVolumeGrids.grid.id
            target.gridHandle = giVolumeGrids.grid.handle
            target.indexGrid = giVolumeGrids.indexGrid.id
            target.indexGridHandle = giVolumeGrids.indexGrid.handle
            target.albedoGrid = giVolumeGrids.albedoGrid.id
            target.albedoGridHandle = giVolumeGrids.albedoGrid.handle
            target.normalGrid = giVolumeGrids.normalGrid.id
            target.normalGridHandle = giVolumeGrids.normalGrid.handle
            target.gridSize = source.giVolumeGrids.gridSize
            target.position.set(source.entity.transform.position)
            target.scale = source.scale
            target.projectionMatrix.set(source.orthoCam.projectionMatrix)
        }
    }

    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        val grids = renderState[voxelGrids]

        val linePoints = mutableListOf<Vector3fc>().apply {
            for(gridIndex in grids.indices) {
                val grid = grids[gridIndex]
                addAABBLines(
                    grid.position.toJoml().sub(Vector3f(grid.gridSizeHalfScaled.toFloat())),
                    grid.position.toJoml().add(Vector3f(grid.gridSizeHalfScaled.toFloat()))
                )
            }
        }
        engineContext.deferredRenderingBuffer.finalBuffer.use(engineContext.gpuContext, false)
        engineContext.gpuContext.blend = false

        engineContext.drawLines(lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
    }

    companion object {
        val gridTextureFormat = GL11.GL_RGBA//GL11.GL_R;
        val gridTextureFormatSized = GL11.GL_RGBA8//GL30.GL_R32UI;
        val indexGridTextureFormat = GL_RED_INTEGER//GL30.GL_R32UI;
        val indexGridTextureFormatSized = GL30.GL_R16I//GL30.GL_R32UI;


        val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
            Transform().get(this)
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
    }
}

class VoxelizerUniformsStatic(val gpuContext: GpuContext<OpenGl>) : StaticFirstPassUniforms(gpuContext) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, PersistentMappedStructBuffer(1, gpuContext, { VoxelGrid() }))
    val writeVoxels by BooleanType(true)
}
class VoxelizerUniformsAnimated(val gpuContext: GpuContext<OpenGl>) : AnimatedFirstPassUniforms(gpuContext) {
    val voxelGridIndex by IntType()
    val voxelGridCount by IntType()
    val voxelGrids by SSBO("VoxelGrid", 5, PersistentMappedStructBuffer(1, gpuContext, { VoxelGrid() }))
    val writeVoxels by BooleanType(true)
}