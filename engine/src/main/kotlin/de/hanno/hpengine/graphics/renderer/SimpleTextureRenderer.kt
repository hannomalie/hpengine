package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.Capability
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.graphics.rendertarget.FrontBufferTarget
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.Facing
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.buffer.vertex.VertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.joml.Vector2f

open class SimpleTextureRenderer(
    protected val graphicsApi: GraphicsApi,
    config: Config,
    var texture: Texture2D,
    private val programManager: ProgramManager,
    private val frontBuffer: FrontBufferTarget,
) : RenderSystem {
    private val fullscreenBuffer = graphicsApi.run { QuadVertexBuffer(graphicsApi) }
    val sixDebugBuffers: List<VertexBuffer> = graphicsApi.run {
        val height = -2f / 3f
        val width = 2f
        val widthDiv = width / 6f
        (0..5).map {
            QuadVertexBuffer(
                graphicsApi,
                QuadVertexBuffer.getPositionsAndTexCoords(
                    Vector2f(-1f + it * widthDiv, -1f),
                    Vector2f(-1 + (it + 1) * widthDiv, height)
                )
            ).apply {
                upload()
            }
        }
    }

    private val renderToQuadProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/fullscreen_quad_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl"))
    )

    val debugFrameProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/quarterscreen_quad_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/debugframe_fragment.glsl"))
    )

    open var finalImage = texture.id

    override fun render(renderState: RenderState) {
        drawToQuad(texture = finalImage)
    }

    fun drawToQuad(
        texture: Int = finalImage,
        buffer: VertexBuffer = fullscreenBuffer,
        program: Program<Uniforms> = renderToQuadProgram,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) {
        draw(texture, buffer, program, factorForDebugRendering, mipMapLevel)
    }

    fun drawToQuad(
        texture: Texture2D,
        buffer: VertexBuffer = fullscreenBuffer,
        program: Program<Uniforms> = renderToQuadProgram,
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
        graphicsApi.run {
            renderTarget.use(graphicsApi, true)
        }

        (0..5).map { faceIndex ->
            val textureView = cubeMapArrayRenderTarget.cubeMapFaceViews[6 * cubeMapIndex + faceIndex]
            draw(
                texture = textureView.id,
                buffer = sixDebugBuffers[faceIndex],
                program = debugFrameProgram
            )
        }
    }

//    TODO: Reimplement
//    fun renderCubeMapDebug(
//        renderTarget: FrontBufferTarget = frontBuffer,
//        cubeMap: OpenGLCubeMap
//    ) = gpuContext.run {
//        renderTarget.use(true)
//        (0..5).map { faceIndex ->
//            val textureView = cubeMap.createView(faceIndex)
//            draw(
//                texture = textureView.id,
//                buffer = sixDebugBuffers[faceIndex],
//                program = debugFrameProgram
//            )
//            textureView.delete()
//        }
//    }

    private fun draw(
        texture: Int,
        buffer: VertexBuffer = fullscreenBuffer,
        program: Program<Uniforms>,
        factorForDebugRendering: Float = 1f,
        mipMapLevel: Int = 0,
    ) = graphicsApi.run {

        polygonMode(Facing.FrontAndBack, RenderingMode.Fill)
        disable(Capability.BLEND)

        program.use()
        program.setUniform("factorForDebugRendering", factorForDebugRendering)
        program.setUniform("mipMapLevel", mipMapLevel)

        disable(Capability.DEPTH_TEST)

        bindTexture(0, TextureTarget.TEXTURE_2D, texture)

        buffer.draw()
    }
}
