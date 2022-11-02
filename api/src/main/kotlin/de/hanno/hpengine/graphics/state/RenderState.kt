package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.graphics.GpuCommandSync

interface IRenderState {
    var gpuCommandSync: GpuCommandSync
}