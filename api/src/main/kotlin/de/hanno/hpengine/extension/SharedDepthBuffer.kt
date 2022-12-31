package de.hanno.hpengine.extension

import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer

data class SharedDepthBuffer(val depthBuffer: DepthBuffer<*>)