package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.createView
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.lwjgl.opengl.GL11

open class SimpleTextureRenderer(
    config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    var texture: Texture,
    private val programManager: ProgramManager<OpenGl>,
    private val frontBuffer: FrontBufferTarget) : RenderSystem {


    private val renderToQuadProgram: Program<Uniforms> = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl")))

    val debugFrameProgram = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/debugframe_fragment.glsl")))

    open var finalImage = texture.id

    final override fun render(result: DrawResult, renderState: RenderState) {
        drawToQuad(texture = finalImage)
    }

    fun drawToQuad(renderTarget: FrontBufferTarget = frontBuffer,
                   texture: Int = finalImage,
                   buffer: VertexBuffer = gpuContext.fullscreenBuffer,
                   program: Program<Uniforms> = renderToQuadProgram,
                   factorForDebugRendering: Float = 1f) {
        draw(renderTarget, texture, buffer, program, false, factorForDebugRendering)
    }

    fun drawToQuad(renderTarget: FrontBufferTarget = frontBuffer,
                   texture: Texture2D,
                   buffer: VertexBuffer = gpuContext.fullscreenBuffer,
                   program: Program<Uniforms> = renderToQuadProgram,
                    factorForDebugRendering: Float = 1f) {
        drawToQuad(renderTarget, texture.id, buffer, program, factorForDebugRendering)
    }

    fun renderCubeMapDebug(renderTarget: FrontBufferTarget = frontBuffer,
                           cubeMapArrayRenderTarget: CubeMapArrayRenderTarget?, cubeMapIndex: Int) {
        if(cubeMapArrayRenderTarget == null) return

        (0..5).map { faceIndex ->
            val textureView = cubeMapArrayRenderTarget.cubeMapFaceViews[6 * cubeMapIndex + faceIndex]
            draw(renderTarget = renderTarget,
                    texture = textureView.id,
                    program = debugFrameProgram,
                    buffer = gpuContext.sixDebugBuffers[faceIndex],
                    clear = false)
        }
    }
    fun renderCubeMapDebug(renderTarget: FrontBufferTarget = frontBuffer,
                           cubeMap: CubeMap) {

        (0..5).map { faceIndex ->
            val textureView = cubeMap.createView(gpuContext, faceIndex)
            draw(renderTarget = renderTarget,
                    texture = textureView.id,
                    program = debugFrameProgram,
                    buffer = gpuContext.sixDebugBuffers[faceIndex],
                    clear = false)
            GL11.glDeleteTextures(textureView.id)
        }
    }
    private fun draw(
            renderTarget: FrontBufferTarget,
            texture: Int,
            buffer: VertexBuffer = gpuContext.fullscreenBuffer,
            program: Program<Uniforms>,
            clear: Boolean,
            factorForDebugRendering: Float = 1f
    ) {

        gpuContext.disable(GlCap.BLEND)

        renderTarget.use(gpuContext, clear = clear)

        program.use()
        program.setUniform("factorForDebugRendering", factorForDebugRendering)

        gpuContext.disable(GlCap.DEPTH_TEST)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}
