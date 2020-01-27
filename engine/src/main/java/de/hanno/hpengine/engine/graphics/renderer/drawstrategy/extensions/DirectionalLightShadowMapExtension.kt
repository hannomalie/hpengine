package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.BLEND
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector4f
import org.lwjgl.opengl.GL30
import java.io.File

class DirectionalLightShadowMapExtension(private val engineContext: EngineContext<OpenGl>) : RenderExtension<OpenGl> {

    private val gpuContext: GpuContext<OpenGl> = engineContext.gpuContext
    val renderTarget = RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(gpuContext, DepthBuffer(gpuContext, SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)),
        name = "DirectionalLight Shadow",
        width = SHADOWMAP_RESOLUTION,
        height = SHADOWMAP_RESOLUTION,
        clear = Vector4f(1f, 1f, 1f, 1f),
    //                Reflective shadowmaps?
    //                .add(new ColorAttachmentDefinitions(new String[]{"Shadow", "Shadow", "Shadow"}, GL30.GL_RGBA32F))
        textures = listOf(ColorAttachmentDefinition("Shadow", GL30.GL_RGBA16F)).toTextures(gpuContext, SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)
    )

    private val directionalShadowPassProgram: Program = engineContext.programManager.getProgram(getShaderSource(File(Shader.directory + "directional_shadowmap_vertex.glsl")), getShaderSource(File(Shader.directory + "shadowmap_fragment.glsl")))
    var voxelConeTracingExtension: VoxelConeTracingExtension? = null

    private var renderedInCycle: Long = 0

    val shadowMapId = renderTarget.renderedTexture
//    val shadowMapWorldPositionId = renderTarget.getRenderedTexture(2)
//    val shadowMapColorMapId = renderTarget.getRenderedTexture(1)

    init {
        engineContext.eventBus.register(this)
    }

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        val task = GPUProfiler.start("Directional shadowmap")
        if (renderedInCycle < renderState.directionalLightHasMovedInCycle ||
                renderedInCycle < renderState.entitiesState.entityMovedInCycle ||
                renderedInCycle < renderState.entitiesState.entityAddedInCycle) {
            drawShadowMap(renderState, firstPassResult)
        }
        task?.end()
    }

    private fun drawShadowMap(renderState: RenderState, firstPassResult: FirstPassResult) {
        gpuContext.disable(BLEND)
        gpuContext.depthMask = true
        gpuContext.enable(DEPTH_TEST)
        gpuContext.setDepthFunc(GlDepthFunc.LESS)
        gpuContext.disable(CULL_FACE)

        // TODO: Better instance culling
        val visibles = renderState.renderBatchesStatic

        //         TODO: Shadowmap should use pipeline for animated object support
        renderTarget.use(gpuContext, true)
        directionalShadowPassProgram.use()
        directionalShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
        directionalShadowPassProgram.bindShaderStorageBuffer(2, renderState.directionalLightState)
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)

        for (i in visibles.indices) {
            val e = visibles[i]
            draw(renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, directionalShadowPassProgram, false, false)
        }
        engineContext.textureManager.generateMipMaps(TEXTURE_2D, shadowMapId)
        firstPassResult.directionalLightShadowMapWasRendered = true

        renderedInCycle = renderState.cycle

    }

    companion object {

        val SHADOWMAP_RESOLUTION = 2048
    }
}
