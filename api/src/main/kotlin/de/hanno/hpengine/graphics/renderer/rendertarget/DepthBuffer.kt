package de.hanno.hpengine.graphics.renderer.rendertarget

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.Texture

context(GraphicsApi)
class DepthBuffer<T : Texture>(val texture: T)