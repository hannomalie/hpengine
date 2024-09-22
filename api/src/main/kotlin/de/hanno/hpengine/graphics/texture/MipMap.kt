package de.hanno.hpengine.graphics.texture

import java.io.File
import javax.imageio.ImageIO
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

fun precalculateMipMapFilesIfNecessary(file: File, dimension: TextureDimension2D): List<File> {
    val mipLevelZeroFile = ImageIO.read(file)
    val bufferedImage = mipLevelZeroFile.apply { DDSConverter.run { rescaleToNextPowerOfTwo() } }
    val mipMapSizes = calculateMipMapSizes(dimension.width, dimension.height)

    val files = getFileAndMipMapFiles(file, mipMapSizes.size).subList(1, mipMapSizes.size)
    val allMipsPrecalculated = files.all { it.exists() }
    return if(allMipsPrecalculated) {
        println("All mipmaps precalculated for ${file.absolutePath}")
        files
    } else {
        mipMapSizes.mapIndexed { index, it ->
            val currentImage = bufferedImage.resize(it.width)
            val buffer = currentImage.toByteBuffer()
            val array = ByteArray(buffer.remaining())
            buffer.get(array)

            ImageIO.write(currentImage, file.extension, files[index])
            println("Saving ${files[index]}")
            files[index]
        }
    }
}
