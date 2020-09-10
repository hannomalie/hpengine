package de.hanno.hpengine.engine.directory

import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource
import java.io.File
import java.nio.file.Files

abstract class AbstractDirectory(val baseDir: File, sourceLocationIsJarFile: Boolean) {
    val usesFileSystem = baseDir.exists() && !sourceLocationIsJarFile

    val tempDir by lazy {
        Files.createTempDirectory(null).toFile()
    }
    init {
        if(usesFileSystem) {
            require(!baseDir.isFile) { "${baseDir.path} is a file, not a directory" }
            require(baseDir.exists()) { "Used baseDir for game doesn't exist: ${baseDir.path}" }
        } else {
            ClassGraph().acceptPaths(baseDir.name).scan().use { scanResult ->
                scanResult.allResources.forEachByteArrayThrowingIOException { res: Resource, content: ByteArray? ->
                    val targetTempFile = tempDir.resolve(res.path)
                    targetTempFile.parentFile.mkdirs()
                    require(targetTempFile.createNewFile()) { "Cannot extract resource from jar to: $targetTempFile" }
                    targetTempFile.writeBytes(content!!)
                }
            }
        }
    }

    fun resolve(path: String): File {
        return if(usesFileSystem) {
            baseDir.resolve(path)
        } else {
            tempDir.resolve(baseDir.name + "/" + path)
        }
    }

    abstract fun toAsset(relativePath: String): Asset
}

sealed class Asset {
    abstract val directory: AbstractDirectory
    abstract val relativePath: String

    fun resolve() = directory.resolve(relativePath)
}
data class EngineAsset(override val directory: EngineDirectory, override val relativePath: String): Asset() {
    companion object {
        fun EngineDirectory.toAsset(relativePath: String) = EngineAsset(this, relativePath)
    }
}
data class GameAsset(override val directory: GameDirectory, override val relativePath: String): Asset() {
    companion object {
        fun GameDirectory.toAsset(relativePath: String) = GameAsset(this, relativePath)
    }
}

class EngineDirectory(baseDir: File): AbstractDirectory(baseDir, EngineDirectory::class.java.protectionDomain.codeSource.location.path.toString().endsWith("jar")) {
    val shaders by lazy { resolve("shaders") }
    val assets by lazy { resolve("assets") }
    val models by lazy { assets.resolve("models") }
    val textures by lazy { assets.resolve("textures") }

    override fun toAsset(relativePath: String) = EngineAsset(this, relativePath)
}
class GameDirectory(baseDir: File, gameClazz: Class<*>?): AbstractDirectory(baseDir, gameClazz?.protectionDomain?.codeSource?.location?.path?.toString()?.endsWith("jar") ?: false) {
    val name = baseDir.name
    val assets by lazy { resolve("assets") }
    val models by lazy { assets.resolve("models") }
    val textures by lazy { assets.resolve("textures") }
    val scripts by lazy { resolve("scripts") }
    val java by lazy { resolve("java") }

    override fun toAsset(relativePath: String) = GameAsset(this, relativePath)
}

inline fun <reified T> GameDirectory(baseDir: File) = GameDirectory(baseDir, T::class.java)