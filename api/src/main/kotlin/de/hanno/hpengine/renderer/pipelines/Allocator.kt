package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.buffers.Buffer

interface Allocator<T : Buffer> {
    fun allocate(capacityInBytes: Int) = allocate(capacityInBytes, null)
    fun allocate(capacityInBytes: Int, current: T?): T
}