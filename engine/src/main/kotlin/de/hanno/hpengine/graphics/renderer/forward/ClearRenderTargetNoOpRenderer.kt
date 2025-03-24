package de.hanno.hpengine.graphics.renderer.forward

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.PrimaryRenderer
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.state.RenderState
import org.koin.core.annotation.Single

@Single(binds = [RenderSystem::class, PrimaryRenderer::class])
class ClearRenderTargetNoOpRenderer(
    private val graphicsApi: GraphicsApi,
    private val renderTarget: RenderTarget2D,
): PrimaryRenderer {
    override val finalOutput = SimpleFinalOutput(renderTarget.textures.first(), 0, this)

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        renderTarget.use(true)
    }
}
