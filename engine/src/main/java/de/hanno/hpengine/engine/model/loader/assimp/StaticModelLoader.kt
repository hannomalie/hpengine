package de.hanno.hpengine.engine.model.loader.assimp

import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.model.IndexedFace
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.Vertex
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.PointerBuffer
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_EMISSIVE
import org.lwjgl.assimp.Assimp.AI_MATKEY_NAME
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_HEIGHT
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR
import java.io.File
import java.nio.IntBuffer
import java.util.ArrayList
import kotlin.math.max


const val defaultFlagsStatic = Assimp.aiProcess_Triangulate + Assimp.aiProcess_JoinIdenticalVertices

class StaticModelLoader(val flags: Int = defaultFlagsStatic) {
    fun load(file: File, materialManager: MaterialManager, resourcesDir: AbstractDirectory): StaticModel {
        val aiScene = Assimp.aiImportFile(resourcesDir.resolve(file).absolutePath, flags) ?: throw IllegalStateException("Cannot load model $file")
        val numMaterials: Int = aiScene.mNumMaterials()
        val aiMaterials: PointerBuffer? = aiScene.mMaterials()
        val deferredMaterials = (0 until numMaterials).map { i ->
            val aiMaterial = AIMaterial.create(aiMaterials!![i])
            GlobalScope.async { aiMaterial.processMaterial(file.parentFile, resourcesDir, materialManager.textureManager) }
        }

        val numMeshes: Int = aiScene.mNumMeshes()
        val aiMeshes: PointerBuffer = aiScene.mMeshes()!!
        val materials = runBlocking {
            deferredMaterials.awaitAll()
        }
        materialManager.addMaterials(materials)
        val meshes: List<StaticMesh> = (0 until numMeshes).map { i ->
            val aiMesh = AIMesh.create(aiMeshes[i])
            aiMesh.processMesh(materials)
        }
        return StaticModel(file.absolutePath, meshes)
    }

    private fun AIMaterial.processMaterial(texturesDir: File, resourcesDir: AbstractDirectory, textureManager: TextureManager): SimpleMaterial {
        fun AIMaterial.retrieveTexture(textureIdentifier: Int): Texture? {
            AIString.calloc().use { path ->
                Assimp.aiGetMaterialTexture(this, textureIdentifier, 0, path, null as IntBuffer?, null, null, null, null, null)
                val textPath = path.dataString()
                return if (textPath.isNotEmpty()) {
                    textureManager.getTexture(texturesDir.resolve(textPath).toString(), directory = resourcesDir)
                } else null
            }
        }

        val name = AIString.calloc()
        aiGetMaterialString(this, AI_MATKEY_NAME, aiTextureType_NONE, 0, name)

        val colour = AIColor4D.create()
        var ambient = Vector4f()
        var result: Int = aiGetMaterialColor(this, AI_MATKEY_COLOR_EMISSIVE, aiTextureType_NONE, 0, colour)
        if (result == 0) {
            ambient = Vector4f(colour.r(), colour.g(), colour.b(), colour.a())
        }
        var diffuse = Vector4f()
        result = aiGetMaterialColor(this, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, colour)
        if (result == 0) {
            diffuse = Vector4f(colour.r(), colour.g(), colour.b(), colour.a())
        }
        val materialInfo = SimpleMaterialInfo(
            name = name.dataString(),
            ambient = max(max(ambient.x, ambient.y), ambient.z),
            diffuse = Vector3f(diffuse.x, diffuse.y, diffuse.z)
        )
        materialInfo.putIfNotNull(SimpleMaterial.MAP.DIFFUSE, retrieveTexture(aiTextureType_DIFFUSE))
        val normalOrHeightMap = retrieveTexture(aiTextureType_NORMALS) ?: retrieveTexture(aiTextureType_HEIGHT)
        materialInfo.putIfNotNull(SimpleMaterial.MAP.NORMAL, normalOrHeightMap)
        materialInfo.putIfNotNull(SimpleMaterial.MAP.SPECULAR, retrieveTexture(aiTextureType_SPECULAR))

        return SimpleMaterial(materialInfo)
    }
    private fun MaterialInfo.putIfNotNull(map: SimpleMaterial.MAP, texture: Texture?) {
        if(texture != null) put(map, texture)
    }
    private fun AIMesh.processMesh(materials: List<Material>): StaticMesh {
        val positions = retrievePositions()
        val normals = retrieveNormals()
        val texCoords = retrieveTexCoords()
        val indices = retrieveFaces()
        val materialIdx = mMaterialIndex()
        val material = if (materialIdx >= 0 && materialIdx < materials.size) {
            materials[materialIdx]
        } else {
            SimpleMaterial(SimpleMaterialInfo(mName().dataString() + "_material"))
        }
        val vertices = positions.indices.map {
            Vertex(positions[it], texCoords[it], normals[it])
        }
        return StaticMesh(mName().dataString(),
                vertices,
                indices,
                material
        )
    }
}

fun AIMesh.retrievePositions(): List<Vector3f> {
    val positions: MutableList<Vector3f> = ArrayList()
    val aiPositions = mVertices()
    while (aiPositions.remaining() > 0) {
        val aiPosition = aiPositions.get()
        positions.add(Vector3f(aiPosition.x(), aiPosition.y(), aiPosition.z()))
    }
    return positions
}

fun AIMesh.retrieveNormals(): List<Vector3f> {
    val normals: MutableList<Vector3f> = ArrayList()
    val aiNormals = mNormals()
    while (aiNormals?.remaining() ?: 0 > 0) {
        val aiNormal = aiNormals!!.get()
        normals.add(Vector3f(aiNormal.x(), aiNormal.y(), aiNormal.z()))
    }
    return normals
}

fun AIMesh.retrieveTexCoords(): List<Vector2f> {
    val texCoords: MutableList<Vector2f> = ArrayList()
    val aiTextureCoords = mTextureCoords(0)
    if(aiTextureCoords != null) {
        while (aiTextureCoords.remaining() > 0) {
            val aiTexCoord = aiTextureCoords.get()
            texCoords.add(Vector2f(aiTexCoord.x(), aiTexCoord.y()))
        }
    } else {
        (0 until mNumVertices()).forEach { texCoords.add(Vector2f()) }
    }
    return texCoords
}

fun AIMesh.retrieveFaces(): List<IndexedFace> {
    val faces: MutableList<IndexedFace> = ArrayList()
    val aiFaces = mFaces()
    while (aiFaces.remaining() > 0) {
        val aiFace = aiFaces.get()
        if(aiFace.mNumIndices() == 3) {
            faces.add(IndexedFace(aiFace.mIndices()[0], aiFace.mIndices()[1], aiFace.mIndices()[2]))
        } else if(aiFace.mNumIndices() == 2) { // no textureCoords
            faces.add(IndexedFace(aiFace.mIndices()[0], aiFace.mIndices()[0], aiFace.mIndices()[1]))
        } else if(aiFace.mNumIndices() == 1) { // no textureCoords, no normals
            faces.add(IndexedFace(aiFace.mIndices()[0], aiFace.mIndices()[0], aiFace.mIndices()[0]))
        } else throw IllegalStateException("Cannot process faces with more than 3 or less than 1 indices. Got indices: ${aiFace.mNumIndices()}")
    }
    return faces
}