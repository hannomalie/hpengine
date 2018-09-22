package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.CustomState
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.struct.copyTo
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import java.io.File

class VoxelGridsState(val voxelGridBuffer: PersistentMappedBuffer): CustomState

class VoxelConeTracingExtension @Throws(Exception::class)
constructor(private val engine: Engine, directionalLightShadowMapExtension: DirectionalLightShadowMapExtension) : RenderExtension {

    val globalGrid = TwoBounceVoxelGrid(256)
    val voxelGridBufferRef = engine.renderManager.renderState.registerState {
        VoxelGridsState(PersistentMappedBuffer(engine.gpuContext, globalGrid.sizeInBytes))
    }

    private val voxelizer: Program = this.engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "voxelize_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "voxelize_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "voxelize_fragment.glsl")), Defines())
    private val voxelConeTraceProgram: Program = this.engine.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl", Defines())
    private val texture3DMipMapAlphaBlendComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_mipmap_alphablend_compute.glsl")
    private val texture3DMipMapComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_mipmap_compute.glsl")
    private val clearDynamicVoxelsComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_clear_dynamic_voxels_compute.glsl")
    private val injectLightComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_inject_light_compute.glsl")
    private val injectMultipleBounceLightComputeProgram: ComputeShaderProgram = this.engine.programManager.getComputeProgram("texture3D_inject_bounce_light_compute.glsl")

    private var lightInjectedCounter: Int = 0

    private val pipeline = SimplePipeline(engine, false, false, false)
    private val firstPassResult = FirstPassResult()
    private val useIndirectDrawing = false
    private var voxelizedInCycle: Long = -1

    private var litInCycle: Long = -1

    init {
        with(globalGrid) {
            engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                    GL11.GL_LINEAR_MIPMAP_LINEAR,
                    GL11.GL_LINEAR,
                    GL12.GL_CLAMP_TO_EDGE).apply {
                        grid = first
                        gridHandle = second
                    }

            engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                    GL11.GL_LINEAR_MIPMAP_LINEAR,
                    GL11.GL_LINEAR,
                    GL12.GL_CLAMP_TO_EDGE).apply {
                        grid2 = first
                        grid2Handle = second
                    }

            engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                    GL11.GL_LINEAR_MIPMAP_LINEAR,
                    GL11.GL_LINEAR,
                    GL12.GL_CLAMP_TO_EDGE).apply {
                        albedoGrid = first
                        albedoGridHandle = second
                    }
            engine.textureManager.getTexture3D(gridSize, gridTextureFormatSized,
                    GL11.GL_NEAREST,
                    GL11.GL_LINEAR,
                    GL12.GL_CLAMP_TO_EDGE).apply {
                        normalGrid = first
                        normalGridHandle = second
                    }

            currentVoxelTarget = grid
            currentVoxelSource = grid2
        }

        Config.getInstance().isUseAmbientOcclusion = false
        directionalLightShadowMapExtension.voxelConeTracingExtension = this
    }

    override fun renderFirstPass(engine: Engine, gpuContext: GpuContext, firstPassResult: FirstPassResult, renderState: RenderState) {
        GPUProfiler.start("VCT first pass")
//         TODO: Move to update somehow

        val gridMoved = if(engine.input.isKeyPressed(GLFW.GLFW_KEY_1)) {
            globalGrid.move(Vector3f(0f, 0f, 1f))
            println(globalGrid.position.z)
            true
        } else false
        val sceneScale = getSceneScale(renderState, globalGrid.gridSizeHalf)
        globalGrid.scale = sceneScale
        val voxelGridState = renderState.getState(voxelGridBufferRef)
        globalGrid.buffer.copyTo(voxelGridState.voxelGridBuffer.buffer, true)

        val entityMoved = renderState.entitiesState.entityAddedInCycle > voxelizedInCycle
        val entityAdded = renderState.entitiesState.entityMovedInCycle > voxelizedInCycle
        val directionalLightMoved = renderState.directionalLightHasMovedInCycle > litInCycle
        val pointlightMoved = renderState.pointLightMovedInCycle > litInCycle
        if (pointlightMoved || directionalLightMoved) {
            lightInjectedCounter = 0
        }
        val useVoxelConeTracing = true
        val clearVoxels = true
        val bounces = 1

        val needsRevoxelization = useVoxelConeTracing && (!renderState.sceneInitiallyDrawn || gridMoved || Config.getInstance().isForceRevoxelization || entityMoved || entityAdded || renderState.entityHasMoved() && renderState.renderBatchesStatic.stream().anyMatch { info -> info.update == Update.DYNAMIC })

        if (needsRevoxelization) {
            lightInjectedCounter = 0
        }
        val needsLightInjection = lightInjectedCounter < bounces || directionalLightMoved

        voxelizeScene(renderState, voxelGridState, clearVoxels, needsRevoxelization)
        injectLight(renderState, bounces, lightInjectedCounter, needsLightInjection)
        GPUProfiler.end()
    }

    fun injectLight(renderState: RenderState, bounces: Int, lightInjectedFramesAgo: Int, needsLightInjection: Boolean) {
        if (needsLightInjection) {
            litInCycle = renderState.cycle
            GPUProfiler.start("grid shading")
            GL42.glBindImageTexture(0, globalGrid.currentVoxelTarget, 0, false, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
            val num_groups_xyz = Math.max(globalGrid.gridSize / 8, 1)

            if (lightInjectedFramesAgo == 0) {
                injectLightComputeProgram.use()

                injectLightComputeProgram.setUniform("pointLightCount", engine.sceneManager.scene.getPointLights().size)
                injectLightComputeProgram.bindShaderStorageBuffer(2, engine.getScene().getPointLightSystem().lightBuffer)
                injectLightComputeProgram.bindShaderStorageBuffer(5, renderState.getState(voxelGridBufferRef).voxelGridBuffer)
                injectLightComputeProgram.setUniform("bounces", bounces)
                injectLightComputeProgram.setUniformAsMatrix4("shadowMatrix", renderState.directionalLightViewProjectionMatrixAsBuffer)
                injectLightComputeProgram.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection)
                injectLightComputeProgram.setUniform("lightColor", renderState.directionalLightState.directionalLightColor)

                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
            } else {
                injectMultipleBounceLightComputeProgram.use()
                injectMultipleBounceLightComputeProgram.setUniform("bounces", bounces)
                injectMultipleBounceLightComputeProgram.setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo)
                injectMultipleBounceLightComputeProgram.bindShaderStorageBuffer(5, renderState.getState(voxelGridBufferRef).voxelGridBuffer)
                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
            }
            lightInjectedCounter++
            mipmapGrid()
            globalGrid.switchCurrentVoxelGrid()
            GPUProfiler.end()
        }

        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        GL11.glColorMask(true, true, true, true)
    }

    fun voxelizeScene(renderState: RenderState, voxelGridState: VoxelGridsState, clearVoxels: Boolean, needsRevoxelization: Boolean) {
        if (needsRevoxelization && clearVoxels) {
            GPUProfiler.start("Clear voxels")
            if (Config.getInstance().isForceRevoxelization || !renderState.sceneInitiallyDrawn) {
                ARBClearTexture.glClearTexImage(globalGrid.currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
                ARBClearTexture.glClearTexImage(globalGrid.normalGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
                ARBClearTexture.glClearTexImage(globalGrid.albedoGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
            } else {
                clearDynamicVoxelsComputeProgram.use()
                val num_groups_xyz = Math.max(globalGrid.gridSize / 8, 1)
                GL42.glBindImageTexture(0, globalGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                GL42.glBindImageTexture(1, globalGrid.normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized)
                GL42.glBindImageTexture(3, globalGrid.currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
                clearDynamicVoxelsComputeProgram.bindShaderStorageBuffer(5, renderState.getState(voxelGridBufferRef).voxelGridBuffer)
                clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz)
            }
            GPUProfiler.end()
        }

        if (needsRevoxelization) {
            voxelizedInCycle = renderState.cycle
            GPUProfiler.start("Voxelization")

            engine.gpuContext.viewPort(0, 0, globalGrid.gridSizeScaled, globalGrid.gridSizeScaled)
            voxelizer.use()
            GL42.glBindImageTexture(3, globalGrid.normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
            GL42.glBindImageTexture(5, globalGrid.albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized)
            voxelizer.setUniformAsMatrix4("shadowMatrix", renderState.directionalLightViewProjectionMatrixAsBuffer)
            voxelizer.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection)
            voxelizer.setUniform("lightColor", renderState.directionalLightState.directionalLightColor)
            voxelizer.bindShaderStorageBuffer(1, renderState.materialBuffer)
            voxelizer.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
            voxelizer.bindShaderStorageBuffer(5, voxelGridState.voxelGridBuffer)

            voxelizer.setUniform("writeVoxels", true)
            engine.gpuContext.depthMask(false)
            engine.gpuContext.disable(DEPTH_TEST)
            engine.gpuContext.disable(BLEND)
            engine.gpuContext.disable(CULL_FACE)
            GL11.glColorMask(false, false, false, false)

            if (useIndirectDrawing && Config.getInstance().isIndirectRendering) {
                firstPassResult.reset()
                pipeline.prepareAndDraw(renderState, voxelizer, voxelizer, firstPassResult)
            } else {
                for (entity in renderState.renderBatchesStatic) {
                    val isStatic = entity.update == Update.STATIC
                    if (renderState.sceneInitiallyDrawn && !Config.getInstance().isForceRevoxelization && isStatic && !renderState.staticEntityHasMoved) {
                        continue
                    }
                    val currentVerticesCount = DrawStrategy.draw(engine.gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, entity, voxelizer, false, false)

                    //                TODO: Count this somehow?
                    //                firstPassResult.verticesDrawn += currentVerticesCount;
                    //                if (currentVerticesCount > 0) {
                    //                    firstPassResult.entitiesDrawn++;
                    //                }
                }
            }
            GPUProfiler.end()
        }
    }

    private fun mipmapGrid() {
        GPUProfiler.start("grid mipmap")
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        mipmapGrid(globalGrid.currentVoxelTarget, texture3DMipMapAlphaBlendComputeProgram)

        GPUProfiler.end()
    }

    private fun mipmapGrid(texture3D: Int, shader: ComputeShaderProgram) {
        shader.use()
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
        GPUProfiler.start("VCT second pass")
        engine.gpuContext.bindTexture(0, TEXTURE_2D, engine.renderer.gBuffer.positionMap)
        engine.gpuContext.bindTexture(1, TEXTURE_2D, engine.renderer.gBuffer.normalMap)
        engine.gpuContext.bindTexture(2, TEXTURE_2D, engine.renderer.gBuffer.colorReflectivenessMap)
        engine.gpuContext.bindTexture(3, TEXTURE_2D, engine.renderer.gBuffer.motionMap)
        engine.gpuContext.bindTexture(7, TEXTURE_2D, engine.renderer.gBuffer.visibilityMap)
        engine.gpuContext.bindTexture(11, TEXTURE_2D, engine.renderer.gBuffer.ambientOcclusionScatteringMap)

        voxelConeTraceProgram.use()
        val camTranslation = Vector3f()
        voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.entity.getTranslation(camTranslation))
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        voxelConeTraceProgram.bindShaderStorageBuffer(0, engine.renderer.gBuffer.storageBuffer)
        voxelConeTraceProgram.bindShaderStorageBuffer(5, renderState.getState(voxelGridBufferRef).voxelGridBuffer)
        voxelConeTraceProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion)
        voxelConeTraceProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        voxelConeTraceProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        engine.gpuContext.fullscreenBuffer.draw()
        //        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
        //        if(entityOrDirectionalLightHasMoved)
        //        {
        //            if only second bounce, clear current target texture
        //            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
        //        }
        GPUProfiler.end()
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

        var ZERO_BUFFER = BufferUtils.createFloatBuffer(4)

        init {
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.rewind()
        }
    }
}
