package de.hanno.hpengine.graphics.renderer

import com.artemis.World
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.model.texture.CubeMap
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.createView
import de.hanno.hpengine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.vertexbuffer.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.lwjgl.opengl.GL11

open class SimpleTextureRenderer(
    config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    var texture: Texture2D,
    private val programManager: ProgramManager<OpenGl>,
    private val frontBuffer: FrontBufferTarget
) : RenderSystem {
    override lateinit var artemisWorld: World

    private val renderToQuadProgram: Program<Uniforms> = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl"))
    )

    val debugFrameProgram = programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/passthrough_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/debugframe_fragment.glsl"))
    )

    open var finalImage = texture.id

    override fun render(result: DrawResult, renderState: RenderState) {
        drawToQuad(texture = finalImage)
    }

    fun drawToQuad(
        texture: Int = finalImage,
        buffer: VertexBuffer = gpuContext.fullscreenBuffer,
        program: Program<Uniforms> = renderToQuadProgram,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {
        draw(texture, buffer, program, factorForDebugRendering, mipMapLevel)
    }

    fun drawToQuad(
        texture: Texture2D,
        buffer: VertexBuffer = gpuContext.fullscreenBuffer,
        program: Program<Uniforms> = renderToQuadProgram,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {
        drawToQuad(texture.id, buffer, program, factorForDebugRendering, mipMapLevel)
    }

    fun renderCubeMapDebug(renderTarget: FrontBufferTarget = frontBuffer,
                           cubeMapArrayRenderTarget: CubeMapArrayRenderTarget?, cubeMapIndex: Int) {
        if(cubeMapArrayRenderTarget == null) return
        renderTarget.use(gpuContext, true)

        (0..5).map { faceIndex ->
            val textureView = cubeMapArrayRenderTarget.cubeMapFaceViews[6 * cubeMapIndex + faceIndex]
            draw(
                texture = textureView.id,
                buffer = gpuContext.sixDebugBuffers[faceIndex],
                program = debugFrameProgram
            )
        }
    }
    fun renderCubeMapDebug(renderTarget: FrontBufferTarget = frontBuffer,
                           cubeMap: CubeMap
    ) {
        renderTarget.use(gpuContext, true)
        (0..5).map { faceIndex ->
            val textureView = cubeMap.createView(gpuContext, faceIndex)
            draw(
                texture = textureView.id,
                buffer = gpuContext.sixDebugBuffers[faceIndex],
                program = debugFrameProgram
            )
            GL11.glDeleteTextures(textureView.id)
        }
    }
    private fun draw(
        texture: Int,
        buffer: VertexBuffer = gpuContext.fullscreenBuffer,
        program: Program<Uniforms>,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {

        gpuContext.disable(GlCap.BLEND)

        program.use()
        program.setUniform("factorForDebugRendering", factorForDebugRendering)
        program.setUniform("mipMapLevel", mipMapLevel)

        gpuContext.disable(GlCap.DEPTH_TEST)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}
