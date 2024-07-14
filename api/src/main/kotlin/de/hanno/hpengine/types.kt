package de.hanno.hpengine

import java.nio.ByteBuffer
import java.nio.IntBuffer

@JvmInline
value class SizeInBytes(val value: Long) {
    constructor(value: Int): this(value.toLong())
    constructor(count: ElementCount, elementSize: SizeInBytes): this(count.value * elementSize.value)

    operator fun plus(other: SizeInBytes) = SizeInBytes(value + other.value)
    operator fun minus(other: SizeInBytes) = SizeInBytes(value - other.value)
    operator fun compareTo(other: SizeInBytes) = value.compareTo(other.value)
}

val Int.Companion.sizeInBytes get() = SizeInBytes(Int.SIZE_BYTES)
val Float.Companion.sizeInBytes get() = SizeInBytes(Float.SIZE_BYTES)
val Double.Companion.sizeInBytes get() = SizeInBytes(Double.SIZE_BYTES)
val Byte.Companion.sizeInBytes get() = SizeInBytes(Byte.SIZE_BYTES)

@JvmInline
value class ElementCount(val value: Long) {
    constructor(value: Int): this(value.toLong())

    operator fun compareTo(other: ElementCount) = value.compareTo(other.value)
    operator fun plus(other: ElementCount) = ElementCount(value + other.value)
    operator fun times(multiplier: Int) = ElementCount(value * multiplier)
    operator fun div(multiplier: Int) = ElementCount(value / multiplier)
    operator fun times(sizeInBytes: SizeInBytes) = SizeInBytes(value * sizeInBytes.value)
}

fun Int.toCount() = ElementCount(this.toLong())
fun Long.toCount() = ElementCount(this)

fun List<ElementCount>.sum() = sumOf { it.value }


fun IntBuffer.position(position: SizeInBytes): IntBuffer = position(position.value.toInt())
fun ByteBuffer.position(position: SizeInBytes): ByteBuffer = position(position.value.toInt())