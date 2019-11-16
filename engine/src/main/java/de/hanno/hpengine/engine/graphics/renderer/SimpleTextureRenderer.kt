package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.draw
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import java.io.File

open class SimpleTextureRenderer(val engineContext: EngineContext<OpenGl>,
                                 var texture: Texture<*>,
                                 programManager: ProgramManager<OpenGl> = engineContext.programManager) : RenderSystem {
    private val gpuContext: GpuContext<OpenGl> = engineContext.gpuContext

    private val renderToQuadProgram: Program = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))

    private val debugFrameProgram = programManager.getProgram(
            getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")),
            getShaderSource(File(Shader.directory + "debugframe_fragment.glsl")))

    open var finalImage = texture.id

    final override fun render(result: DrawResult, state: RenderState) {
        drawToQuad(texture = finalImage)
    }

    fun drawToQuad(renderTarget: RenderTarget<Texture2D> = engineContext.window.frontBuffer,
                   texture: Int = finalImage,
                   buffer: VertexBuffer = gpuContext.fullscreenBuffer,
                   program: Program = renderToQuadProgram) {
        draw(renderTarget, texture, buffer, program, true)
    }

    fun drawToQuad(renderTarget: RenderTarget<Texture2D> = engineContext.window.frontBuffer,
                   texture: Texture2D,
                   buffer: VertexBuffer = gpuContext.fullscreenBuffer,
                   program: Program = renderToQuadProgram) {
        drawToQuad(renderTarget, texture.id, buffer, program)
    }

    fun renderCubeMapDebug(renderTarget: RenderTarget<Texture2D> = engineContext.window.frontBuffer,
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
    private fun draw(renderTarget: RenderTarget<Texture2D>, texture: Int, buffer: VertexBuffer = gpuContext.fullscreenBuffer, program: Program, clear: Boolean) {

        gpuContext.disable(GlCap.BLEND)

        renderTarget.use(gpuContext, clear = clear)

        program.use()

        gpuContext.disable(GlCap.DEPTH_TEST)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}
