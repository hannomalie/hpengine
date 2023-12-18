package de.hanno.hpengine.graphics.renderer.forward

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.output.DebugOutput
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.texture.Texture2D
import org.joml.Vector4f
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


data class ForwardFinalOutput(override var texture2D: Texture2D, override var mipmapLevel: Int = 0, override val producedBy: RenderSystem? = null): FinalOutput

@Module
@ComponentScan
class SimpleForwardRenderingModule {
    @Single
    fun renderTarget(graphicsApi: GraphicsApi, config: Config) = graphicsApi.run {
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

    @Single
    fun debugOutput() = DebugOutput(null, 0)
}