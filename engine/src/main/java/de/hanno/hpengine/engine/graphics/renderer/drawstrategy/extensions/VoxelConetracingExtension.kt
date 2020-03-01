package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.SizedArray
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.BLEND
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_3D
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.struct.copyTo
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.ARBClearTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL42
import java.io.File
import kotlin.math.max

class VoxelGridsState(val voxelGridBuffer: PersistentMappedBuffer)

class VoxelConeTracingExtension(
        private val engine: EngineContext<OpenGl>,
        directionalLightShadowMapExtension: DirectionalLightShadowMapExtension?,
        val renderer: RenderSystem) : RenderExtension<OpenGl> {

    val voxelGrids = SizedArray(2) { VoxelGrid() }.apply {
        array[0].initGrid(Vector3f(50f, 0f, 0f))
        array[1].apply {
            initGrid(Vector3f(-50f, 0f, 0f))
            scale = 1.0f
        }
    }

    private fun VoxelGrid.initGrid(position: Vector3f) {
        gridSize = 256
        setPosition(position)

        engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                MinFilter.LINEAR_MIPMAP_LINEAR,
                MagFilter.LINEAR,
                GL12.GL_CLAMP_TO_EDGE).apply {
            grid = id
            gridHandle = handle
        }
        engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                MinFilter.LINEAR_MIPMAP_LINEAR,
                MagFilter.LINEAR,
                GL12.GL_CLAMP_TO_EDGE).apply {
            grid2 = id
            grid2Handle = handle
        }

        engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                MinFilter.LINEAR_MIPMAP_LINEAR,
                MagFilter.LINEAR,
                GL12.GL_CLAMP_TO_EDGE).apply {
            albedoGrid = id
            albedoGridHandle = handle
        }
        engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                MinFilter.LINEAR_MIPMAP_LINEAR,
                MagFilter.LINEAR,
                GL12.GL_CLAMP_TO_EDGE).apply {
            normalGrid = id
            normalGridHandle = handle
        }
//             TODO: Add emissive
    }

    val voxelGridBufferRef = engine.renderStateManager.renderState.registerState {
        VoxelGridsState(PersistentMappedBuffer(engine.gpuContext, voxelGrids.sizeInBytes))
    }

    private val voxelizer: Program = this.engine.programManager.getProgram(getShaderSource(File(Shader.directory + "voxelize_vertex.glsl")), getShaderSource(File(Shader.directory + "voxelize_fragment.glsl")), getShaderSource(File(Shader.directory + "voxelize_geometry.glsl")))
    private val voxelConeTraceProgram: Program = this.engine.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl")
    private val texture3DMipMapAlphaBlendComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_mipmap_alphablend_compute.glsl")
    private val texture3DMipMapComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_mipmap_compute.glsl")
    private val clearDynamicVoxelsComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_clear_dynamic_voxels_compute.glsl")
    private val injectLightComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_inject_light_compute.glsl")
    private val injectMultipleBounceLightComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_inject_bounce_light_compute.glsl")

    private var lightInjectedFramesAgo: Int = 0

    private val pipeline = SimplePipeline(engine,
            useFrustumCulling = false,
            useBackFaceCulling = false,
            useLineDrawingIfActivated = false)
    private val firstPassResult = FirstPassResult()
    private val useIndirectDrawing = false
    private var voxelizedInCycle: Long = -1

    private var litInCycle: Long = -1

    init {
        directionalLightShadowMapExtension?.voxelConeTracingExtension = this
    }

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        if(!renderState.sceneInitialized) return

        profiled("VCT first pass") {

            //         TODO: Move to update somehow

            val movableGrid = voxelGrids[1]
            val gridMoved = if(backend.input.isKeyPressed(GLFW.GLFW_KEY_1)) {
                movableGrid.move(Vector3f(-1.5f, 0f, 0f))
                println(movableGrid.position.x)
                true
            } else if(backend.input.isKeyPressed(GLFW.GLFW_KEY_2)) {
                movableGrid.move(Vector3f(1.5f, 0f, 0f))
                println(movableGrid.position.x)
                true
            } else false
            val globalGrid = voxelGrids[0]
            globalGrid.setPosition(Vector3f(0f,0f,0f))
            val sceneScale = getSceneScale(renderState, globalGrid.gridSizeHalf)
            globalGrid.scale = sceneScale // TODO: scenescale for first grid, other scale for other grids
            val voxelGridState = renderState.get(voxelGridBufferRef)
            voxelGrids.buffer.copyTo(voxelGridState.voxelGridBuffer.buffer, true)

            val entityMoved = renderState.entitiesState.entityAddedInCycle > voxelizedInCycle
            val entityAdded = renderState.entitiesState.entityMovedInCycle > voxelizedInCycle
            val directionalLightMoved = renderState.directionalLightHasMovedInCycle > litInCycle
            val pointlightMoved = renderState.pointLightMovedInCycle > litInCycle
            val useVoxelConeTracing = true
            val clearVoxels = true
            val bounces = 1

            val needsRevoxelization = useVoxelConeTracing && (!renderState.sceneInitiallyDrawn || gridMoved || engine.config.debug.isForceRevoxelization || entityMoved || entityAdded || renderState.entityHasMoved() && renderState.renderBatchesStatic.stream().anyMatch { info -> info.update == Update.DYNAMIC })

            if (needsRevoxelization || directionalLightMoved || pointlightMoved) {
                lightInjectedFramesAgo = 0
            }
            val needsLightInjection = lightInjectedFramesAgo < bounces

            voxelizeScene(renderState, voxelGridState, clearVoxels, needsRevoxelization)
            injectLight(renderState, bounces, needsLightInjection)
            engine.config.debug.isForceRevoxelization = false
        }
    }

    fun injectLight(renderState: RenderState, bounces: Int, needsLightInjection: Boolean) {
        if (needsLightInjection) {
            litInCycle = renderState.cycle
            profiled("grid shading") {
                val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
                for(voxelGridIndex in 0 until maxGridCount) {
                    val currentVoxelGrid = voxelGrids[voxelGridIndex]

                    val numGroupsXyz = max(currentVoxelGrid.gridSize / 8, 1)

                    if(lightInjectedFramesAgo == 0) {
                        with(injectLightComputeProgram) {
                            use()
                            engine.gpuContext.bindTexture(1, TEXTURE_3D, voxelGrids[0].albedoGrid)
                            engine.gpuContext.bindTexture(2, TEXTURE_3D, voxelGrids[0].normalGrid)
                            GL42.glBindImageTexture(0, currentVoxelGrid.grid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                            setUniform("pointLightCount", renderState.lightState.pointLights.size)
                            bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                            bindShaderStorageBuffer(3, renderState.directionalLightState)
                            bindShaderStorageBuffer(5, renderState[voxelGridBufferRef].voxelGridBuffer)
                            setUniform("bounces", bounces)
                            setUniform("voxelGridIndex", voxelGridIndex)

                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
                            mipmapGrid(currentVoxelGrid.grid)
                        }
                    }
//                    if(bounces > 1 && lightInjectedFramesAgo == 0) {//> 0) {
//                        with(injectMultipleBounceLightComputeProgram) {
//                            use()
//                            GL42.glBindImageTexture(0, currentVoxelGrid.grid2, 0, false, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
//                            setUniform("bounces", bounces)
//                            setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo)
//                            setUniform("voxelGridIndex", voxelGridIndex)
//                            bindShaderStorageBuffer(5, renderState.get(voxelGridBufferRef).voxelGridBuffer)
//                            dispatchCompute(numGroupsXyz, numGroupsXyz, numGroupsXyz)
//                            mipmapGrid(currentVoxelGrid.grid2)
//                        }
//                    }
                }

                GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                lightInjectedFramesAgo++
            }
        }
        GL11.glColorMask(true, true, true, true)
    }

    fun voxelizeScene(renderState: RenderState, voxelGridState: VoxelGridsState, clearVoxels: Boolean, needsRevoxelization: Boolean) {
        val maxGridCount = if(engine.gpuContext.isSupported(BindlessTextures)) voxelGrids.size else 1
        for(voxelGridIndex in 0 until maxGridCount) {
            val currentVoxelGrid = voxelGrids[voxelGridIndex]
            if (needsRevoxelization && clearVoxels) {
                profiled("Clear voxels") {
                    if (engine.config.debug.isForceRevoxelization || !renderState.sceneInitiallyDrawn) {
                        ARBClearTexture.glClearTexImage(currentVoxelGrid.grid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                        ARBClearTexture.glClearTexImage(currentVoxelGrid.grid2, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                        ARBClearTexture.glClearTexImage(currentVoxelGrid.normalGrid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                        ARBClearTexture.glClearTexImage(currentVoxelGrid.albedoGrid, 0, gridTextureFormat, GL11.GL_FLOAT, ZERO_BUFFER)
                    } else {
                        clearDynamicVoxelsComputeProgram.use()
                        val num_groups_xyz = Math.max(currentVoxelGrid.gridSize / 8, 1)
                        GL42.glBindImageTexture(0, currentVoxelGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                        GL42.glBindImageTexture(1, currentVoxelGrid.normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized)
                        GL42.glBindImageTexture(3, currentVoxelGrid.currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                        clearDynamicVoxelsComputeProgram.bindShaderStorageBuffer(5, renderState.get(voxelGridBufferRef).voxelGridBuffer)
                        clearDynamicVoxelsComputeProgram.setUniform("voxelGridIndex", voxelGridIndex)
                        clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
                    }
                }
            }

            if (needsRevoxelization) {
                voxelizedInCycle = renderState.cycle
                profiled("Voxelization") {
                    engine.gpuContext.viewPort(0, 0, currentVoxelGrid.gridSizeScaled, currentVoxelGrid.gridSizeScaled)
                    voxelizer.use()
                    GL42.glBindImageTexture(3, currentVoxelGrid.normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                    GL42.glBindImageTexture(5, currentVoxelGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                    GL42.glBindImageTexture(6, currentVoxelGrid.grid2, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                    voxelizer.setUniform("voxelGridIndex", voxelGridIndex)
                    voxelizer.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                    voxelizer.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                    voxelizer.bindShaderStorageBuffer(5, voxelGridState.voxelGridBuffer)

                    voxelizer.setUniform("writeVoxels", true)
                    engine.gpuContext.depthMask = false
                    engine.gpuContext.disable(DEPTH_TEST)
                    engine.gpuContext.disable(BLEND)
                    engine.gpuContext.disable(CULL_FACE)
                    GL11.glColorMask(false, false, false, false)

                    if (useIndirectDrawing && engine.config.performance.isIndirectRendering) {
                        firstPassResult.reset()
                        pipeline.draw(renderState, voxelizer, voxelizer, firstPassResult)
                    } else {
                        for (entity in renderState.renderBatchesStatic) {
                            val isStatic = entity.update == Update.STATIC
                            if (renderState.sceneInitiallyDrawn && !engine.config.debug.isForceRevoxelization
                                    && isStatic && !renderState.staticEntityHasMoved) {
                                continue
                            }
                            voxelizer.setTextureUniforms(engine.gpuContext, entity.materialInfo.maps)
                            draw(renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, entity, voxelizer, false, false)
                        }
                    }
                }

                engine.textureManager.generateMipMaps(TEXTURE_3D, currentVoxelGrid.albedoGrid)

                if(engine.config.debug.isDebugVoxels) {
                    GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
                    mipmapGrid(currentVoxelGrid.currentVoxelSource, texture3DMipMapAlphaBlendComputeProgram)
                }
            }
        }
    }

    override fun update() = pipeline.prepare(engine.renderStateManager.renderState.currentWriteState)

    private fun mipmapGrid(textureId: Int) = profiled("grid mipmap") {
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        mipmapGrid(textureId, texture3DMipMapAlphaBlendComputeProgram)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
    }

    private fun mipmapGrid(texture3D: Int, shader: ComputeShaderProgram) {
        shader.use()
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

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        if(!renderState.sceneInitialized) return
        profiled("VCT second pass") {
            engine.gpuContext.bindTexture(0, TEXTURE_2D, engine.deferredRenderingBuffer.positionMap)
            engine.gpuContext.bindTexture(1, TEXTURE_2D, engine.deferredRenderingBuffer.normalMap)
            engine.gpuContext.bindTexture(2, TEXTURE_2D, engine.deferredRenderingBuffer.colorReflectivenessMap)
            engine.gpuContext.bindTexture(3, TEXTURE_2D, engine.deferredRenderingBuffer.motionMap)
            engine.gpuContext.bindTexture(7, TEXTURE_2D, engine.deferredRenderingBuffer.visibilityMap)
            engine.gpuContext.bindTexture(9, TEXTURE_3D, voxelGrids[0].grid)
            engine.gpuContext.bindTexture(10, TEXTURE_3D, voxelGrids[0].grid2)
            engine.gpuContext.bindTexture(11, TEXTURE_2D, engine.deferredRenderingBuffer.ambientOcclusionScatteringMap)
            engine.gpuContext.bindTexture(12, TEXTURE_3D, voxelGrids[0].albedoGrid)
            engine.gpuContext.bindTexture(13, TEXTURE_3D, voxelGrids[0].normalGrid)

            voxelConeTraceProgram.use()
            val camTranslation = Vector3f()
            voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.entity.getTranslation(camTranslation))
            voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
            voxelConeTraceProgram.bindShaderStorageBuffer(0, engine.deferredRenderingBuffer.exposureBuffer)
            voxelConeTraceProgram.bindShaderStorageBuffer(5, renderState.get(voxelGridBufferRef).voxelGridBuffer)
            voxelConeTraceProgram.setUniform("useAmbientOcclusion", engine.config.quality.isUseAmbientOcclusion)
            voxelConeTraceProgram.setUniform("screenWidth", engine.config.width.toFloat())
            voxelConeTraceProgram.setUniform("screenHeight", engine.config.height.toFloat())
            voxelConeTraceProgram.setUniform("skyBoxMaterialIndex", renderState.skyBoxMaterialIndex)
            voxelConeTraceProgram.setUniform("debugVoxels", engine.config.debug.isDebugVoxels)
            engine.gpuContext.fullscreenBuffer.draw()
            //        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
            //        if(entityOrDirectionalLightHasMoved)
            //        {
            //            if only second bounce, clear current target texture
            //            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
            //        }
        }
    }

    private var maxExtents = Vector4f()
    fun getSceneScale(renderState: RenderState, gridSizeHalf: Int): Float {
        maxExtents.x = Math.max(Math.abs(renderState.sceneMin.x), Math.abs(renderState.sceneMax.x))
        maxExtents.y = Math.max(Math.abs(renderState.sceneMin.y), Math.abs(renderState.sceneMax.y))
        maxExtents.z = Math.max(Math.abs(renderState.sceneMin.z), Math.abs(renderState.sceneMax.z))
        val max = Math.max(Math.max(maxExtents.x, maxExtents.y), maxExtents.z)
        var sceneScale = max / gridSizeHalf.toFloat()
        sceneScale = Math.max(sceneScale, 1.0f)
        return sceneScale
    }

    companion object {
        val gridTextureFormat = GL11.GL_RGBA//GL11.GL_R;
        val gridTextureFormatSized = GL11.GL_RGBA8//GL30.GL_R32UI;

        var ZERO_BUFFER = BufferUtils.createFloatBuffer(4).apply {
            put(0f)
            put(0f)
            put(0f)
            put(0f)
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
