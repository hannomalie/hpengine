package de.hanno.hpengine.graphics.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.Texture2D


class CubeMapRenderTarget(
    graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<CubeMap>
) : BackBufferRenderTarget<CubeMap> by renderTarget

class RenderTarget2D(
    graphicsApi: GraphicsApi,
    renderTarget: BackBufferRenderTarget<Texture2D>
) : BackBufferRenderTarget<Texture2D> by renderTarget
