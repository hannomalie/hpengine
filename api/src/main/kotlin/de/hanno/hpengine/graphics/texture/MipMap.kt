package de.hanno.hpengine.graphics.texture

import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.nextUp

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

fun getMipMapCountForDimension(w: Int, h: Int, d: Int): Int {
    return 1 + floor(log2(max(w, max(h, d)).toDouble())).nextUp().toInt()
}

fun calculateMipMapSizes(width: Int, height: Int): List<TextureDimension2D> {
    val mipMapCount = getMipMapCountForDimension(width, height, 0)
    val widths = mutableListOf<Int>().apply {
        add(width)
    }
    val heights = mutableListOf<Int>().apply {
        add(height)
    }
    var nextWidth = max(1, floor(width * 0.5).toInt())
    var nextHeight = max(1, floor(height * 0.5).toInt())
    (0 until mipMapCount - 1).forEach { _ ->
        widths.add(nextWidth)
        heights.add(nextHeight)
        nextWidth = max(1, floor(nextWidth * 0.5).toInt())
        nextHeight = max(1, floor(nextHeight * 0.5).toInt())
    }
    val mipMapDimensions = widths.mapIndexed { index, width ->
        TextureDimension2D(width, heights[index])
    }
    return mipMapDimensions
}
