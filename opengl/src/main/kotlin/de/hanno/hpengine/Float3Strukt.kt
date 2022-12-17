package de.hanno.hpengine

import struktgen.api.Strukt
import java.nio.ByteBuffer


interface Float3: Strukt {
    context(ByteBuffer) val a: Float
    context(ByteBuffer) val b: Float
    context(ByteBuffer) val c: Float
    companion object
}