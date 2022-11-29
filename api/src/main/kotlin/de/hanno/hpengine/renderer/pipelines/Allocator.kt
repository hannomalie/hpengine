package de.hanno.hpengine.graphics.renderer.pipelines

interface Allocator<T : Buffer> {
    fun allocate(capacityInBytes: Int) = allocate(capacityInBytes, null)
    fun allocate(capacityInBytes: Int, current: T?): T
}