package de.hanno.hpengine

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.extension.renderSystem
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import org.joml.Vector4f
import org.koin.dsl.module


val textureRendererModule = module {
    single {
        val config: Config = get()

        get<GraphicsApi>().run {
            RenderTarget(
                frameBuffer = FrameBuffer(
                    depthBuffer = DepthBuffer(config.width, config.height)
                ),
                width = config.width,
                height = config.height,
                textures = listOf(
                    ColorAttachmentDefinition("Color", InternalTextureFormat.RGBA8)
                ).toTextures(
                    this,
                    config.width, config.height
                ),
                name = "Final Image",
                clear = Vector4f(),
            )
        }
    }
    single {
        val renderTarget: RenderTarget2D = get()
        FinalOutput(renderTarget.textures.first())
    }
    renderSystem {
        val textureManager: OpenGLTextureManager = get()
        val renderTarget: RenderTarget2D = get()

        object : SimpleTextureRenderer(get<GraphicsApi>(), get(), textureManager.defaultTexture.backingTexture, get(), get()) {
            override val sharedRenderTarget = renderTarget
            override val requiresClearSharedRenderTarget = true

            override fun render(renderState: RenderState) {
                texture?.let { texture ->
                    graphicsApi.clearColor(1f, 0f, 0f, 1f)
                    drawToQuad(texture = texture)
                }
            }
        }
    }
}