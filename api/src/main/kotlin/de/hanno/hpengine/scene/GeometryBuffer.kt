package de.hanno.hpengine.scene

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import struktgen.api.Strukt

sealed interface GeometryBuffer<T:Strukt> {
    var vertexStructArray: TypedGpuBuffer<T>
}

sealed interface GeometryOffset {
    val vertexOffset: ElementCount
}