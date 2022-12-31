package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.texture.Texture

context(GpuContext)
class DepthBuffer<T : Texture>(val texture: T)