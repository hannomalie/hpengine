package de.hanno.hpengine.directory

import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource
import org.apache.logging.log4j.LogManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

abstract class AbstractDirectory(val baseDir: File, sourceLocationIsJarFile: Boolean) {
    private val logger = LogManager.getLogger(AbstractDirectory::class.java)

    val useExistingFolder = baseDir.exists() && !sourceLocationIsJarFile
    private val path = Path.of(System.getProperty("java.io.tmpdir"))
        .resolve("hpengine")
        .apply { if(!exists()) createDirectory() }

    val tempDir by lazy {
        Files.createTempDirectory(path, null).toFile().apply {
            deleteOnExit()
        }
    }

    init {
        logger.info("Initializing directory")
        if(useExistingFolder) {
            require(!baseDir.isFile) { "${baseDir.path} is a file, not a directory" }
            require(baseDir.exists()) { "Used baseDir for game doesn't exist: ${baseDir.path}" }
        } else {
            extractJarContentToTempDir()
        }
        logger.info("Finished initializing")
    }

    private fun extractJarContentToTempDir() {
        ClassGraph().acceptPaths(baseDir.name).scan().use { scanResult ->
            scanResult.allResources.forEachByteArrayThrowingIOException { res: Resource, content: ByteArray? ->
                val targetTempFile = tempDir.resolve(res.path)
                targetTempFile.parentFile.mkdirs()
                require(targetTempFile.createNewFile()) { "Cannot extract resource from jar to: $targetTempFile" }
                targetTempFile.writeBytes(content!!)
            }
        }
    }

    fun resolve(path: String): File {
        val path = if(path.startsWith("/")) path.replaceFirst("/", "") else path
        return if(useExistingFolder) {
            baseDir.resolve(path)
        } else {
            tempDir.resolve("${baseDir.name}/$path")
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

data class EngineDirectory(val _baseDir: File): AbstractDirectory(_baseDir, EngineDirectory::class.java.protectionDomain.codeSource.location.path.toString().endsWith("jar")) {
    val shaders by lazy { resolve("shaders") }
    val assets by lazy { resolve("assets") }
    val models by lazy { assets.resolve("models") }
    val textures by lazy { assets.resolve("textures") }

    override fun toAsset(relativePath: String) = EngineAsset(this, relativePath)
}
data class GameDirectory(val _baseDir: File, val gameClazz: Class<*>?): AbstractDirectory(
    baseDir = _baseDir,
    sourceLocationIsJarFile = gameClazz?.protectionDomain?.codeSource?.location?.path?.toString()?.endsWith("jar") ?: false
) {
    val name = baseDir.name
    val assets by lazy { resolve("assets") }
    val models by lazy { assets.resolve("models") }
    val textures by lazy { assets.resolve("textures") }
    val scripts by lazy { resolve("scripts") }
    val java by lazy { resolve("java") }

    override fun toAsset(relativePath: String) = GameAsset(this, relativePath)
}

inline fun <reified T> GameDirectory(baseDir: File) = GameDirectory(baseDir, T::class.java)