package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import com.artemis.World
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
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
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.joml.Vector4f
import org.lwjgl.opengl.GL30

class DirectionalLightShadowMapExtension(
    val config: Config,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
    val gpuContext: GpuContext<OpenGl>
) : DeferredRenderExtension<OpenGl> {

    private var forceRerender = true

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
    ).apply {
        factorsForDebugRendering[0] = 100f
    }

    private val directionalShadowPassProgram: Program<Uniforms> = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
            Uniforms.Empty)

    private var renderedInCycle: Long = 0

    val shadowMapId = renderTarget.renderedTexture

    override fun extract(renderState: RenderState, world: World) {
        renderState.directionalLightState[0].shadowMapHandle = renderTarget.renderedTextureHandles[0]
        renderState.directionalLightState[0].shadowMapId = renderTarget.renderedTextures[0]
    }
    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        profiled("Directional shadowmap") {
            val needsRerender =  forceRerender ||
                    renderedInCycle < renderState.directionalLightHasMovedInCycle ||
                    renderedInCycle < renderState.entitiesState.anyEntityMovedInCycle ||
                    renderedInCycle < renderState.entitiesState.entityAddedInCycle ||
                    renderState.entitiesState.renderBatchesAnimated.isNotEmpty()

            if (needsRerender) {
                drawShadowMap(renderState, firstPassResult)
            }
        }
    }

    private fun drawShadowMap(renderState: RenderState, firstPassResult: FirstPassResult) {
        gpuContext.disable(BLEND)
        gpuContext.depthMask = true
        gpuContext.enable(DEPTH_TEST)
        gpuContext.depthFunc = GlDepthFunc.LESS
        gpuContext.disable(CULL_FACE)

        // TODO: Better instance culling
        val visibles = renderState.renderBatchesStatic

        //         TODO: Shadowmap should use pipeline for animated object support
        renderTarget.use(gpuContext, true)
        directionalShadowPassProgram.use()
        directionalShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
        directionalShadowPassProgram.bindShaderStorageBuffer(2, renderState.directionalLightState)
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
        directionalShadowPassProgram.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer)
        directionalShadowPassProgram.bindShaderStorageBuffer(7, renderState.entitiesState.vertexIndexBufferStatic.vertexStructArray)

        renderState.vertexIndexBufferStatic.indexBuffer.bind()
        for (batch in visibles) {
            if(batch.isShadowCasting) {
                renderState.vertexIndexBufferStatic.indexBuffer.draw(batch, directionalShadowPassProgram, bindIndexBuffer = false)
            }
        }
        textureManager.generateMipMaps(TEXTURE_2D, shadowMapId)
        firstPassResult.directionalLightShadowMapWasRendered = true

        renderedInCycle = renderState.cycle
        forceRerender = false
    }

    companion object {

        val SHADOWMAP_RESOLUTION = 2048
    }
}
