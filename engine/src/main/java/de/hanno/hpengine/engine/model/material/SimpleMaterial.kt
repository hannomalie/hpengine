package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.log.ConsoleLogger
import java.lang.Double.BYTES
import java.lang.Float

interface Material {
    var materialIndex: Int
    val materialInfo: MaterialInfo

    fun put(map: SimpleMaterial.MAP, texture: Texture) {
        materialInfo.put(map, texture)
    }
    fun remove(map: SimpleMaterial.MAP) {
        materialInfo.remove(map)
    }
}

class SimpleMaterial(override val materialInfo: MaterialInfo): Material {

    override var materialIndex = -1

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
        OCCLUSION("occlusionMap", 3),
        HEIGHT("heightMap", 4),
        REFLECTION("reflectionMap", 5),
        ENVIRONMENT("environmentMap", 6),
        ROUGHNESS("roughnessMap", 7);

        val uniformKey: String = "has" + shaderVariableName[0].toUpperCase() + shaderVariableName.substring(1)
    }

    enum class ENVIRONMENTMAP_TYPE {
        PROVIDED,
        GENERATED
    }

    override fun toString(): String = materialInfo.name

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SimpleMaterial) {
            return false
        }

        return materialInfo.name == other.materialInfo.name
    }

    override fun hashCode(): Int = materialInfo.name.hashCode()

    companion object {

        const val bytesPerObject = 10 * Float.BYTES + 6 * Integer.BYTES + 6 * BYTES + 2 * Integer.BYTES

        var MIPMAP_DEFAULT = true

        private val LOGGER = ConsoleLogger.getLogger()

        val directory: String
            get() = Directories.WORKDIR_NAME + "/assets/materials/"
    }
}
