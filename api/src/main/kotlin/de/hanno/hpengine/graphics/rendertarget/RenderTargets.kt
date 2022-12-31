package de.hanno.hpengine.graphics.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.Texture2D


context(GraphicsApi)
class CubeMapRenderTarget(
    renderTarget: BackBufferRenderTarget<CubeMap>
) : BackBufferRenderTarget<CubeMap> by renderTarget

context(GraphicsApi)
class RenderTarget2D(
    renderTarget: BackBufferRenderTarget<Texture2D>
) : BackBufferRenderTarget<Texture2D> by renderTarget
