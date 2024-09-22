package de.hanno.hpengine.graphics.texture

import ddsutil.DDSUtil
import jogl.DDSImage
import org.apache.commons.io.FilenameUtils
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.locks.ReentrantLock

object DDSConverter {
    val DDSUtilWriteLock = ReentrantLock()

    fun BufferedImage.saveAsDDS(path: String): BufferedImage {
        val rescaledImage = rescaleToNextPowerOfTwo()
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

    fun getFullPathAsDDS(fileName: String): String {
        val name = FilenameUtils.getBaseName(fileName)
        val nameWithExtension = FilenameUtils.getName(fileName)
        var restPath: String? = fileName.replace(nameWithExtension.toRegex(), "")
        if (restPath == null) {
            restPath = ""
        }
        return "$restPath$name.dds"
    }

    fun availableAsDDS(path: String): Boolean {
        val fileAsDds = fileAsDDS(path)
        return fileAsDds.exists()
    }

    fun fileAsDDS(path: String) = File(path.split(".")[0] + ".dds")
}
