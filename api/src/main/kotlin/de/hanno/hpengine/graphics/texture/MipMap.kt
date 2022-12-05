package de.hanno.hpengine.graphics.texture

import kotlin.math.max

fun calculateMipMapCountPlusOne(width: Int, height: Int) = calculateMipMapCount(max(width, height)) + 1

fun calculateMipMapCount(width: Int, height: Int) = calculateMipMapCount(max(width, height))

fun calculateMipMapCount(size: Int): Int {
    var maxLength = size
    var count = 0
    while (maxLength >= 1) {
        count++
        maxLength /= 2
    }
    return count
}
