package de.hanno.hpengine.model.material

import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.graphics.texture.*
import org.joml.Vector2f
import org.joml.Vector3f

enum class WorldSpaceTexCoords {
    No, XY, ZY, XZ
}
data class Material(
    val name: String,
    val diffuse: Vector3f = Vector3f(1f, 1f, 1f),
    var roughness: Float = 0.95f,
    var metallic: Float = 0f,
    var ambient: Float = 0f,
    var transparency: Float = 0f,
    var parallaxScale: Float = 0.04f,
    var parallaxBias: Float = 0.02f,
    var uvScale: Vector2f = Vector2f(1.0f, 1.0f),
    var lodFactor: Float = 100f,
    var worldSpaceTexCoords: WorldSpaceTexCoords = WorldSpaceTexCoords.No,
    var materialType: MaterialType = MaterialType.DEFAULT,
    var transparencyType: TransparencyType = TransparencyType.BINARY,
    var cullBackFaces: Boolean = materialType != MaterialType.FOLIAGE,
    var cullFrontFaces: Boolean = false,
    var renderPriority: Int? = null,
    var writesDepth: Boolean = true,
    var depthTest: Boolean = true,
    val maps: MutableMap<MAP, TextureHandle<*>> = mutableMapOf(),
    var environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED,
    var isShadowCasting: Boolean = true,
    var neverCull: Boolean = false,
    var programDescription: ProgramDescription? = null,
) {

    val cullingEnabled get() = cullBackFaces || cullFrontFaces
    init {
        require(name.isNotEmpty()) { "Name may not empty for material! $this" }
    }
    enum class MaterialType {
        DEFAULT,
        FOLIAGE,
        UNLIT
    }
    enum class TransparencyType(val needsForwardRendering: Boolean) {
        BINARY(false),
        FULL(true)
    }

    enum class MAP(val shaderVariableName: String, val textureSlot: Int) {
        DIFFUSE("diffuseMap", 0),
        NORMAL("normalMap", 1),
        SPECULAR("specularMap", 2),
        DISPLACEMENT("displacementMap", 3),
        HEIGHT("heightMap", 4),
        REFLECTION("reflectionMap", 5),
        ENVIRONMENT("environmentMap", 6),
        ROUGHNESS("roughnessMap", 7);

        val uniformKey: String = "has" + shaderVariableName[0].uppercaseChar() + shaderVariableName.substring(1)
    }

    enum class ENVIRONMENTMAP_TYPE {
        PROVIDED,
        GENERATED
    }

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Material) {
            return false
        }

        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    fun put(map: MAP, texture: TextureHandle<*>) {
        maps[map] = texture
    }

    fun remove(map: MAP) {
        maps.remove(map)
    }

    companion object {
        val directory: String get() = Directories.ENGINEDIR_NAME + "/assets/materials/"
    }
}

fun deriveHandle(handle: TextureHandle<*>?, fallbackTexture: TextureHandle<Texture2D>? = null): TextureHandle<out Texture>? {
    return when (handle) {
        is DynamicHandle -> when (val texture = handle.texture) {
            null -> handle.fallback ?: fallbackTexture
            else -> when (val fallback = handle.fallback) {
                null -> handle
                else -> {
                    val imageCountToUseFromActualImage = texture.imageCount - (fallback.texture.imageCount - fallback.currentMipMapBias)
                    if (
                        handle.uploadState !is UploadState.ForceFallback &&
                        handle.uploadState !is UploadState.MarkedForUpload &&
                        handle.currentMipMapBias <= imageCountToUseFromActualImage
                    ) {
                        handle
                    } else {
                        fallback
                    }
                }
            }
        }
        is StaticHandle -> handle
        null -> null
    }
}
