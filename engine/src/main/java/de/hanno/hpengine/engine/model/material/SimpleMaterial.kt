package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.log.ConsoleLogger
import org.apache.commons.io.FilenameUtils
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.Double.*
import java.lang.Float
import java.nio.ByteBuffer

interface Material: Bufferable {
    var materialInfo: MaterialInfo

    fun put(map: SimpleMaterial.MAP, texture: Texture<TextureDimension2D>): MaterialInfo {
        materialInfo = materialInfo.put(map, texture)
        return materialInfo
    }
    fun remove(map: SimpleMaterial.MAP): MaterialInfo {
        materialInfo = materialInfo.remove(map)
        return materialInfo
    }
}

class SimpleMaterial(override var materialInfo: MaterialInfo): Material, Serializable {

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
        OCCLUSION("occlusionMap", 3),
        HEIGHT("heightMap", 4),
        REFLECTION("reflectionMap", 5),
        ENVIRONMENT("environmentMap", 6),
        ROUGHNESS("roughnessMap", 7)
    }

    enum class ENVIRONMENTMAP_TYPE {
        PROVIDED,
        GENERATED
    }

    fun init(materialManager: MaterialManager) {
    }

    override fun toString(): String {
        return materialInfo.name
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SimpleMaterial) {
            return false
        }

        return materialInfo.name == other.materialInfo.name
    }

    override fun hashCode(): Int {
        return materialInfo.name.hashCode()
    }


    override fun putToBuffer(buffer: ByteBuffer) {
        buffer.putFloat(materialInfo.diffuse.x)
        buffer.putFloat(materialInfo.diffuse.y)
        buffer.putFloat(materialInfo.diffuse.z)
        buffer.putFloat(materialInfo.metallic)

        buffer.putFloat(materialInfo.roughness)
        buffer.putFloat(materialInfo.ambient)
        buffer.putFloat(materialInfo.parallaxBias)
        buffer.putFloat(materialInfo.parallaxScale)

        buffer.putFloat(materialInfo.transparency)
        buffer.putInt(materialInfo.materialType.ordinal)
        buffer.putFloat(0.0f)
        buffer.putFloat(0.0f)
        buffer.putDouble(if (materialInfo.getHasDiffuseMap()) longBitsToDouble(materialInfo.maps[MAP.DIFFUSE]!!.handle) else -1.0)
        buffer.putDouble(if (materialInfo.getHasNormalMap()) longBitsToDouble(materialInfo.maps[MAP.NORMAL]!!.handle) else -1.0)
        buffer.putDouble(if (materialInfo.getHasSpecularMap()) longBitsToDouble(materialInfo.maps[MAP.SPECULAR]!!.handle) else -1.0)
        buffer.putDouble(if (materialInfo.getHasHeightMap()) longBitsToDouble(materialInfo.maps[MAP.HEIGHT]!!.handle) else -1.0)
        buffer.putDouble(if (materialInfo.getHasOcclusionMap()) longBitsToDouble(materialInfo.maps[MAP.OCCLUSION]!!.handle) else -1.0)
        buffer.putDouble(if (materialInfo.getHasRoughnessMap()) longBitsToDouble(materialInfo.maps[MAP.ROUGHNESS]!!.handle) else -1.0)
    }

    override fun debugPrintFromBuffer(buffer: ByteBuffer): String {
        return debugPrintFromBufferStatic(buffer)
    }

    override fun getBytesPerObject(): Int {
        return Companion.bytesPerObject
    }


    companion object {

        const val bytesPerObject = 10 * Float.BYTES + 6 * Integer.BYTES + 6 * BYTES + 2 * Integer.BYTES
        private const val serialVersionUID = 1L

        var MIPMAP_DEFAULT = true

        private val LOGGER = ConsoleLogger.getLogger()

        fun write(material: SimpleMaterial, resourceName: String): Boolean {
            val fileName = FilenameUtils.getBaseName(resourceName)
            var fos: FileOutputStream? = null
            var out: ObjectOutputStream? = null
            try {
                fos = FileOutputStream("$directory$fileName.hpmaterial")
                out = ObjectOutputStream(fos)

                out.writeObject(material)

            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    out!!.close()
                    fos!!.close()
                    return true
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
            return false
        }

        val directory: String
            get() = Directories.WORKDIR_NAME + "/assets/materials/"

        fun debugPrintFromBufferStatic(buffer: ByteBuffer): String {
            val builder = StringBuilder()
                    .append("DiffuseX " + buffer.float).append("\n")
                    .append("DiffuseY " + buffer.float).append("\n")
                    .append("DiffuseZ " + buffer.float).append("\n")
                    .append("Metallic " + buffer.float).append("\n")
                    .append("Roughness " + buffer.float).append("\n")
                    .append("Ambient " + buffer.float).append("\n")
                    .append("ParallaxBias " + buffer.float).append("\n")
                    .append("ParallaxScale " + buffer.float).append("\n")
                    .append("Transparency " + buffer.float).append("\n")
                    .append("Type " + MaterialType.values()[buffer.float.toInt()]).append("\n")
                    .append("HasDiffuseMap " + buffer.int).append("\n")
                    .append("HasNormalMap " + buffer.int).append("\n")
                    .append("HasSpecularMap " + buffer.int).append("\n")
                    .append("HasHeightMap " + buffer.int).append("\n")
                    .append("HasOcclusionMap " + buffer.int).append("\n")
                    .append("HasRoughnessMap " + buffer.int).append("\n")
                    .append("Diffuse handle " + buffer.double).append("\n")
                    .append("Normal handle " + buffer.double).append("\n")
                    .append("Specular handle " + buffer.double).append("\n")
                    .append("Height handle " + buffer.double).append("\n")
                    .append("Occlusion handle " + buffer.double).append("\n")
                    .append("Roughness handle " + buffer.double).append("\n")
                    .append("Placeholder " + buffer.int).append("\n")
                    .append("Placeholder " + buffer.int).append("\n")
            val resultString = builder.toString()
            println(resultString)
            return resultString
        }
    }
}
