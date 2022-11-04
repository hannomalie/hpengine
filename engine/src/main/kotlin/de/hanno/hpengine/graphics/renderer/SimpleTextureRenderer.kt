package de.hanno.hpengine.graphics.renderer

import com.artemis.World

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.graphics.renderer.rendertarget.FrontBufferTarget
import de.hanno.hpengine.graphics.shader.IProgram
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.vertexbuffer.IVertexBuffer
import de.hanno.hpengine.model.texture.OpenGLCubeMap
import de.hanno.hpengine.model.texture.OpenGLTexture2D
import de.hanno.hpengine.model.texture.createView
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.lwjgl.opengl.GL11

open class SimpleTextureRenderer(
    config: Config,
    private val gpuContext: GpuContext,
    var texture: OpenGLTexture2D,
    private val programManager: ProgramManager,
    private val frontBuffer: FrontBufferTarget
) : RenderSystem {
    override lateinit var artemisWorld: World

    private val renderToQuadProgram = programManager.getProgram(
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
        buffer: IVertexBuffer = gpuContext.fullscreenBuffer,
        program: IProgram<Uniforms> = renderToQuadProgram,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {
        draw(texture, buffer, program, factorForDebugRendering, mipMapLevel)
    }

    fun drawToQuad(
        texture: OpenGLTexture2D,
        buffer: IVertexBuffer = gpuContext.fullscreenBuffer,
        program: IProgram<Uniforms> = renderToQuadProgram,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {
        drawToQuad(texture.id, buffer, program, factorForDebugRendering, mipMapLevel)
    }

    fun renderCubeMapDebug(
        renderTarget: FrontBufferTarget = frontBuffer,
        cubeMapArrayRenderTarget: CubeMapArrayRenderTarget?, cubeMapIndex: Int
    ) {
        if (cubeMapArrayRenderTarget == null) return
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

    fun renderCubeMapDebug(
        renderTarget: FrontBufferTarget = frontBuffer,
        cubeMap: OpenGLCubeMap
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
        buffer: IVertexBuffer = gpuContext.fullscreenBuffer,
        program: IProgram<Uniforms>,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {

        gpuContext.disable(Capability.BLEND)

        program.use()
        program.setUniform("factorForDebugRendering", factorForDebugRendering)
        program.setUniform("mipMapLevel", mipMapLevel)

        gpuContext.disable(Capability.DEPTH_TEST)

        gpuContext.bindTexture(0, TextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}
