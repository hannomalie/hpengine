package de.hanno.hpengine.graphics.renderer.forward

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.texture.Texture2D
import org.joml.Vector4f
import org.koin.core.scope.Scope
import org.koin.dsl.binds
import org.koin.dsl.module


data class SimpleFinalOutput(override var texture2D: Texture2D, override var mipmapLevel: Int = 0, override val producedBy: RenderSystem? = null): FinalOutput

val simpleForwardRenderingModule = module {
    single {
        createRenderTargetDefinition()
    }
    single { ColorOnlyRenderer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) } binds(arrayOf(
        RenderSystem::class,de.hanno.hpengine.graphics.PrimaryRenderer::class))
}

val visibilityRendererModule = module {
    single {
        createRenderTargetDefinition()
    }
    single { VisibilityRenderer(get(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get()) } binds(arrayOf(RenderSystem::class,de.hanno.hpengine.graphics.PrimaryRenderer::class))
}

val noOpRendererModule = module {
    single {
        createRenderTargetDefinition()
    }
    single { ClearRenderTargetNoOpRenderer(get(),get()) } binds(arrayOf(RenderSystem::class,de.hanno.hpengine.graphics.PrimaryRenderer::class))
}
fun Scope.createRenderTargetDefinition(): RenderTarget2D {
    val config = get<Config>()
    val graphicsApi = get<GraphicsApi>()

    return createFinalImageRenderTarget(graphicsApi, config)
}

fun createFinalImageRenderTarget(
    graphicsApi: GraphicsApi,
    config: Config,
) = graphicsApi.RenderTarget(
    frameBuffer = graphicsApi.FrameBuffer(
        depthBuffer = graphicsApi.DepthBuffer(config.width, config.height)
    ),
    width = config.width,
    height = config.height,
    textures = listOf(
        ColorAttachmentDefinition("Color", InternalTextureFormat.RGBA8)
    ).toTextures(
        graphicsApi,
        config.width, config.height
    ),
    name = "Final Image",
    clear = Vector4f(),
)
