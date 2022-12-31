package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.Texture2D


context(GpuContext)
class CubeMapRenderTarget(
    renderTarget: BackBufferRenderTarget<CubeMap>
) : BackBufferRenderTarget<CubeMap> by renderTarget

context(GpuContext)
class RenderTarget2D(
    renderTarget: BackBufferRenderTarget<Texture2D>
) : BackBufferRenderTarget<Texture2D> by renderTarget
