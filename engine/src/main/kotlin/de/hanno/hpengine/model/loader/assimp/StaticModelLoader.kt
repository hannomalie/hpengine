package de.hanno.hpengine.model.loader.assimp

import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.model.IndexedTriangle
import de.hanno.hpengine.model.StaticMesh
import de.hanno.hpengine.model.StaticModel
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.transform.AABBData
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
import org.lwjgl.assimp.AIVector3D
import org.lwjgl.assimp.Assimp
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_EMISSIVE
import org.lwjgl.assimp.Assimp.AI_MATKEY_NAME
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_DISPLACEMENT
import org.lwjgl.assimp.Assimp.aiTextureType_HEIGHT
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR
import java.io.File
import java.nio.IntBuffer
import java.nio.file.Path
import java.util.ArrayList
import kotlin.math.max


const val defaultFlagsStatic = Assimp.aiProcess_Triangulate + Assimp.aiProcess_JoinIdenticalVertices + Assimp.aiProcess_GenNormals + Assimp.aiProcess_GenSmoothNormals

class StaticModelLoader(val flags: Int = defaultFlagsStatic) {
    fun load(file: String, textureManager: OpenGLTextureManager, resourcesDir: AbstractDirectory): StaticModel {
        val absolutePath = resourcesDir.resolve(file).absolutePath.apply {
            check(File(this).exists()) { "File $this does not exist, can't load static model" }
        }
        val aiScene = Assimp.aiImportFile(absolutePath, flags) ?: throw IllegalStateException("Cannot load model $absolutePath")
        val numMaterials: Int = aiScene.mNumMaterials()
        val aiMaterials: PointerBuffer? = aiScene.mMaterials()
        val deferredMaterials = (0 until numMaterials).map { i ->
            val aiMaterial = AIMaterial.create(aiMaterials!![i])
            GlobalScope.async { aiMaterial.processMaterial(Path.of(file).parent.toString(), resourcesDir, textureManager) }
        }

        val numMeshes: Int = aiScene.mNumMeshes()
        val aiMeshes: PointerBuffer = aiScene.mMeshes()!!
        val materials = runBlocking {
            deferredMaterials.awaitAll()
        }
        val meshes: List<StaticMesh> = (0 until numMeshes).map { i ->
            val aiMesh = AIMesh.create(aiMeshes[i])
            aiMesh.processMesh(materials)
        }

        return StaticModel(resourcesDir.resolve(file), meshes)
    }

    private fun AIMaterial.processMaterial(texturesDir: String, resourcesDir: AbstractDirectory, textureManager: OpenGLTextureManager): Material {
        fun AIMaterial.retrieveTexture(textureIdentifier: Int): Texture? {
            AIString.calloc().use { path ->
                Assimp.aiGetMaterialTexture(this, textureIdentifier, 0, path, null as IntBuffer?, null, null, null, null, null)
                val textPath = path.dataString()
                return if (textPath.isNotEmpty()) {
                    textureManager.getTexture("$texturesDir/$textPath", directory = resourcesDir, unloadable = true)
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
        val material = Material(
            name.dataString(),
            ambient = max(max(ambient.x, ambient.y), ambient.z),
            diffuse = Vector3f(diffuse.x, diffuse.y, diffuse.z)
        )
        material.putIfNotNull(Material.MAP.DIFFUSE, retrieveTexture(aiTextureType_DIFFUSE))
        // super odd, but assimp seems to interpret normal maps as type height map O.O
        material.putIfNotNull(Material.MAP.NORMAL, retrieveTexture(aiTextureType_NORMALS) ?: retrieveTexture(aiTextureType_HEIGHT))

        // TODO: I can't do ?: retrieveTexture(aiTextureType_HEIGHT) because the height map is most often given, but contains normals....
        material.putIfNotNull(Material.MAP.HEIGHT, retrieveTexture(aiTextureType_DISPLACEMENT))
        material.putIfNotNull(Material.MAP.SPECULAR, retrieveTexture(aiTextureType_SPECULAR))

        return material
    }
    private fun Material.putIfNotNull(map: Material.MAP, texture: Texture?) {
        if(texture != null) put(map, texture)
    }
    private fun AIMesh.processMesh(materials: List<Material>): StaticMesh {
        val positions = retrievePositions()
        val normals = retrieveNormals().let { it.ifEmpty { (positions.indices).map { Vector3f(0f,1f,0f) } } }
        val texCoords = retrieveTexCoords()
        val indexedTriangles = retrieveFaces()
        val materialIdx = mMaterialIndex()
        val material = if (materialIdx >= 0 && materialIdx < materials.size) {
            materials[materialIdx]
        } else {
            Material(mName().dataString() + "_material")
        }
        val vertices = positions.indices.map {
            Vertex(positions[it], texCoords[it], normals[it])
        }
        return StaticMesh(
            mName().dataString(),
            vertices,
            indexedTriangles,
            material
        )
    }
}

fun AIMesh.retrievePositions(): List<Vector3f> {
    val positions: MutableList<Vector3f> = ArrayList()
    val aiPositions = mVertices()
    while (aiPositions.remaining() > 0) {
        val aiPosition = aiPositions.get()
        positions.add(aiPosition.toVector3f())
    }
    return positions
}

fun AIMesh.retrieveNormals(): List<Vector3f> {
    val normals: MutableList<Vector3f> = ArrayList()
    val aiNormals = mNormals()
    while ((aiNormals?.remaining() ?: 0) > 0) {
        val aiNormal = aiNormals!!.get()
        normals.add(aiNormal.toVector3f())
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

fun AIMesh.retrieveFaces(): List<IndexedTriangle> {
    val faces: MutableList<IndexedTriangle> = ArrayList()
    val aiFaces = mFaces()
    while (aiFaces.remaining() > 0) {
        val aiFace = aiFaces.get()
        if(aiFace.mNumIndices() == 3) {
            faces.add(IndexedTriangle(aiFace.mIndices()[0], aiFace.mIndices()[1], aiFace.mIndices()[2]))
        } else if(aiFace.mNumIndices() == 2) { // no textureCoords
            faces.add(IndexedTriangle(aiFace.mIndices()[0], aiFace.mIndices()[0], aiFace.mIndices()[1]))
        } else if(aiFace.mNumIndices() == 1) { // no textureCoords, no normals
            faces.add(IndexedTriangle(aiFace.mIndices()[0], aiFace.mIndices()[0], aiFace.mIndices()[0]))
        } else throw IllegalStateException("Cannot process faces with more than 3 or less than 1 indices. Got indices: ${aiFace.mNumIndices()}")
    }
    return faces
}

fun AIMesh.retrieveAABB(): AABBData {
    val aiAabb = mAABB()
    return AABBData(aiAabb.mMin().toVector3f(), aiAabb.mMax().toVector3f())
}

fun AIVector3D.toVector3f() = Vector3f(x(), y(), z())