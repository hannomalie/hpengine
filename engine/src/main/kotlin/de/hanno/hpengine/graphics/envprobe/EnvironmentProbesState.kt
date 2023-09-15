package de.hanno.hpengine.graphics.envprobe

import org.lwjgl.BufferUtils

class EnvironmentProbesState {
    var environmapsArray3Id: Int = -1
    var environmapsArray0Id: Int = -1
    var activeProbeCount = 0

//    TODO: Pass buffer contents by value
    var environmentMapMin = BufferUtils.createFloatBuffer(4)
    var environmentMapMax = BufferUtils.createFloatBuffer(4)
    var environmentMapWeights = BufferUtils.createFloatBuffer(4)
}