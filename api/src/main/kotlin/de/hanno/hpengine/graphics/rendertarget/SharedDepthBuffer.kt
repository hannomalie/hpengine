package de.hanno.hpengine.graphics.rendertarget

import org.koin.core.annotation.Single

@Single
data class SharedDepthBuffer(val depthBuffer: DepthBuffer<*>)