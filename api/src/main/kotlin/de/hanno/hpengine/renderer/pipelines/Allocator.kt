package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.Buffer

interface Allocator<T : Buffer> {
    fun allocate(capacityInBytes: SizeInBytes) = allocate(capacityInBytes, null)
    fun allocate(capacityInBytes: SizeInBytes, current: T?): T
}