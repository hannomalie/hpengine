package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.model.texture.Texture

data class Material(
    val name: String,
    var materialInfo: MaterialInfo
) {

    init {
        require(name.isNotEmpty()) { "Name may not empty for material! $materialInfo" }
    }
    var materialIndex = -1

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
    fun put(map: MAP, texture: Texture) {
        materialInfo.put(map, texture)
    }

    fun remove(map: MAP) {
        materialInfo.remove(map)
    }

    companion object {

        var MIPMAP_DEFAULT = true

        val directory: String
            get() = Directories.ENGINEDIR_NAME + "/assets/materials/"
    }
}
