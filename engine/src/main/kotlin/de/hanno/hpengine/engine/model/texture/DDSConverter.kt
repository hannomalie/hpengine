package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import ddsutil.ImageRescaler
import jogl.DDSImage
import org.apache.commons.io.FilenameUtils
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.locks.ReentrantLock

object DDSConverter {
    val DDSUtilWriteLock = ReentrantLock()

    fun BufferedImage.saveAsDDS(path: String): BufferedImage {
        val rescaledImage = this.rescaleToNextPowerOfTwo()
        val ddsFile = File(getFullPathAsDDS(path))
        if (ddsFile.exists()) {
            ddsFile.delete()
        }
        DDSUtilWriteLock.lock()
        try {
            DDSUtil.write(ddsFile, rescaledImage, DDSImage.D3DFMT_DXT5, true)
        } finally {
            DDSUtilWriteLock.unlock()
        }
        return rescaledImage
    }

    fun BufferedImage.rescaleToNextPowerOfTwo(): BufferedImage {
        val oldWidth = this.width
        val nextPowerOfTwoWidth = getNextPowerOfTwo(oldWidth)
        val oldHeight = this.height
        val nextPowerOfTwoHeight = getNextPowerOfTwo(oldHeight)
        var result = this

        val maxOfWidthAndHeightPoT = Math.max(nextPowerOfTwoWidth, nextPowerOfTwoHeight)

        if (oldWidth != nextPowerOfTwoWidth || oldHeight != nextPowerOfTwoHeight) {
            result = ImageRescaler().rescaleBI(this, maxOfWidthAndHeightPoT, maxOfWidthAndHeightPoT)
        }
        return result
    }

    fun getFullPathAsDDS(fileName: String): String {
        val name = FilenameUtils.getBaseName(fileName)
        val nameWithExtension = FilenameUtils.getName(fileName)
        var restPath: String? = fileName.replace(nameWithExtension.toRegex(), "")
        if (restPath == null) {
            restPath = ""
        }
        return "$restPath$name.dds"
    }

    fun getNextPowerOfTwo(fold: Int): Int {
        var ret = 2
        while (ret < fold) {
            ret *= 2
        }
        return ret
    }

    fun availableAsDDS(path: String): Boolean {
        val fileAsDds = fileAsDDS(path)
        return fileAsDds.exists()
    }

    fun fileAsDDS(path: String) = File(path.split(".")[0] + ".dds")
}