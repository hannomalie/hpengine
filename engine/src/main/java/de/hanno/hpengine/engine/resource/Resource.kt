package de.hanno.hpengine.engine.resource

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path


interface Resource {
    val path: Path
    val file: File
}

class FileSystemResource(override val path: Path): Resource {
    override val file
        get() = path.toFile()
}
class ClassPathResource(override val path: Path): Resource {
    override val file: File
        get() {
            val fileInTempDir = tempDir.resolve(path).toFile()
            if(!fileInTempDir.exists()) {
                fileInTempDir.parentFile.mkdirs()
                require(fileInTempDir.createNewFile()) { "Cannot create file ${fileInTempDir.path}" }
                val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(path.toString())
                require(inputStream != null) { "$path seems to not exist in resources" }
                FileOutputStream(fileInTempDir).use { out ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                require(fileInTempDir.exists()) { "File doesn't exist, even after copying: ${fileInTempDir.path}" }
            }
            return fileInTempDir
        }

    companion object {
        val tempDir by lazy {
            Files.createTempDirectory(null)
        }
    }
}