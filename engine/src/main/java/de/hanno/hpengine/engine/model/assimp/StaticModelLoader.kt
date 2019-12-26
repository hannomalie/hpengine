package de.hanno.hpengine.engine.model.assimp

import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.model.AnimatedFrame
import de.hanno.hpengine.engine.model.AnimatedMesh
import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.Animation
import de.hanno.hpengine.engine.model.IndexedFace
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.Node
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.Vertex
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4i
import org.lwjgl.PointerBuffer
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_EMISSIVE
import org.lwjgl.assimp.Assimp.AI_MATKEY_NAME
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_HEIGHT
import org.lwjgl.assimp.Assimp.aiTextureType_NONE
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR
import java.io.File
import java.nio.IntBuffer
import java.util.ArrayList
import kotlin.math.max


const val flags = Assimp.aiProcess_Triangulate + Assimp.aiProcess_JoinIdenticalVertices
const val flagsAnimated = flags + aiProcess_LimitBoneWeights

class StaticModelLoader {
    fun load(file: File, materialManager: MaterialManager, resourcesDir: AbstractDirectory): StaticModel {
        val aiScene = Assimp.aiImportFile(resourcesDir.resolve(file).absolutePath, flags) ?: throw IllegalStateException("Cannot load model $file")
        val numMaterials: Int = aiScene.mNumMaterials()
        val aiMaterials: PointerBuffer? = aiScene.mMaterials()
        val materials = (0 until numMaterials).map { i ->
            val aiMaterial = AIMaterial.create(aiMaterials!![i])
            aiMaterial.processMaterial(file.parentFile, materialManager, resourcesDir)
        }

        val numMeshes: Int = aiScene.mNumMeshes()
        val aiMeshes: PointerBuffer = aiScene.mMeshes()!!
        val meshes: List<StaticMesh> = (0 until numMeshes).map { i ->
            val aiMesh = AIMesh.create(aiMeshes[i])
            aiMesh.processMesh(materials)
        }
        return StaticModel(file.absolutePath, meshes)
    }

    private fun AIMaterial.processMaterial(texturesDir: File, materialManager: MaterialManager, resourcesDir: AbstractDirectory): Material {
        val textureManager = materialManager.textureManager
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

        return materialManager.getMaterial(materialInfo)
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

class AnimatedModelLoader {
    fun load(file: File, materialManager: MaterialManager, resourcesDir: AbstractDirectory): AnimatedModel {
        val aiScene = Assimp.aiImportFile(resourcesDir.resolve(file).absolutePath, flagsAnimated) ?: throw IllegalStateException("Cannot load model $file")
        val numMaterials: Int = aiScene.mNumMaterials()
        val aiMaterials: PointerBuffer? = aiScene.mMaterials()
        val materials = (0 until numMaterials).map { i ->
            val aiMaterial = AIMaterial.create(aiMaterials!![i])
            aiMaterial.processMaterial(file.parentFile, materialManager, resourcesDir)
        }


        val boneList: MutableList<Bone> = ArrayList()
        val numMeshes: Int = aiScene.mNumMeshes()
        val aiMeshes: PointerBuffer = aiScene.mMeshes()!!
        val meshes: List<AnimatedMesh> = (0 until numMeshes).map { i ->
            val aiMesh = AIMesh.create(aiMeshes[i])
            aiMesh.processMesh(materials, boneList)
        }
        val aiRootNode = aiScene.mRootNode()
        val rootTransfromation: Matrix4f = aiRootNode!!.mTransformation().toMatrix()
        val rootNode: Node = aiRootNode.processNodesHierarchy(null)
        val animations: Map<String, Animation> = aiScene.processAnimations(boneList, rootNode, rootTransfromation)

        return AnimatedModel(file.absolutePath, meshes, animations)
    }
    private fun AIScene.processAnimations(boneList: List<Bone>, rootNode: Node,
                                          rootTransformation: Matrix4f): Map<String, Animation> {
        val animations: MutableMap<String, Animation> = HashMap()
        // Process all animations
        val numAnimations = mNumAnimations()
        val aiAnimations = mAnimations()
        for (i in 0 until numAnimations) {
            val aiAnimation = AIAnimation.create(aiAnimations!![i])
            // Calculate transformation matrices for each node
            val numChannels = aiAnimation.mNumChannels()
            val aiChannels = aiAnimation.mChannels()
            for (j in 0 until numChannels) {
                val aiNodeAnim = AINodeAnim.create(aiChannels!![j])
                val nodeName = aiNodeAnim.mNodeName().dataString()
                val node: Node = rootNode.findByName(nodeName)!!
                buildTransFormationMatrices(aiNodeAnim, node)
            }
            val frames: List<AnimatedFrame> = buildAnimationFrames(boneList, rootNode, rootTransformation)
            val animation = Animation(aiAnimation.mName().dataString().takeUnless { it.isBlank() } ?: "Default",
                    frames,
                    aiAnimation.mDuration().toFloat(),
                    aiAnimation.mTicksPerSecond().toFloat())
            animations[animation.name] = animation
        }
        return animations
    }

    private fun buildTransFormationMatrices(aiNodeAnim: AINodeAnim, node: Node) {
        val numFrames = aiNodeAnim.mNumPositionKeys()
        val positionKeys = aiNodeAnim.mPositionKeys()!!
        val scalingKeys = aiNodeAnim.mScalingKeys()!!
        val rotationKeys = aiNodeAnim.mRotationKeys()!!

        for (i in 0 until numFrames) {
            var aiVecKey = positionKeys.get(i)
            var vec = aiVecKey.mValue()

            val transfMat = Matrix4f().translate(vec.x(), vec.y(), vec.z())

            val quatKey = rotationKeys.get(i)
            val aiQuat = quatKey.mValue()
            val quat = Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w())
            transfMat.rotate(quat)

            if (i < aiNodeAnim.mNumScalingKeys()) {
                aiVecKey = scalingKeys.get(i)
                vec = aiVecKey.mValue()
                transfMat.scale(vec.x(), vec.y(), vec.z())
            }

            node.addTransformation(transfMat)
        }
    }

    fun buildAnimationFrames(boneList: List<Bone>,rootNode: Node, rootTransformation: Matrix4f): List<AnimatedFrame> {
        val numFrames = rootNode.animationFrames
        val frameList = ArrayList<AnimatedFrame>()
        for (i in 0 until numFrames) {
            val frame = AnimatedFrame()
            frameList.add(frame)

            val numBones = boneList.size
            for (j in 0 until numBones) {
                val bone = boneList.get(j)
                val node = rootNode.findByName(bone.name)
                var boneMatrix = Node.getParentTransforms(node, i)
                boneMatrix.mul(bone.offsetMatrix)
                boneMatrix = Matrix4f(rootTransformation).mul(boneMatrix)
                frame.setMatrix(j, boneMatrix)
            }
        }

        return frameList
    }

    private fun AINode.processNodesHierarchy(parentNode: Node?): Node {
        val nodeName = mName().dataString()
        val node = Node(nodeName, parentNode)
        val numChildren = mNumChildren()
        val aiChildren = mChildren()
        for (i in 0 until numChildren) {
            val aiChildNode = AINode.create(aiChildren!![i])
            val childNode: Node = aiChildNode.processNodesHierarchy(node)
            node.addChild(childNode)
        }
        return node
    }
    private fun AIMaterial.processMaterial(texturesDir: File, materialManager: MaterialManager, resourcesDir: AbstractDirectory): Material {
        val textureManager = materialManager.textureManager
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

        return materialManager.getMaterial(materialInfo)
    }
    private fun MaterialInfo.putIfNotNull(map: SimpleMaterial.MAP, texture: Texture?) {
        if(texture != null) put(map, texture)
    }
    private fun AIMesh.processMesh(materials: List<Material>, bones: MutableList<Bone>): AnimatedMesh {

        val positions = retrievePositions()
        val normals = retrieveNormals()
        val texCoords = retrieveTexCoords()
        val indices = retrieveFaces()
        val (boneIds, weights) = retrieveBonesAndWeights(bones)
        val materialIdx = mMaterialIndex()
        val material = if (materialIdx >= 0 && materialIdx < materials.size) {
            materials[materialIdx]
        } else {
            SimpleMaterial(SimpleMaterialInfo(mName().dataString() + "_material"))
        }
        val vertices = positions.indices.map {
            AnimatedVertex(positions[it],
                texCoords[it],
                runCatching { normals[it] }.getOrElse { Vector3f(0f,1f,0f) },
                Vector4f(weights[4*it+0], weights[4*it+1], weights[4*it+2], weights[4*it+3]),
                Vector4i(boneIds[4*it+0], boneIds[4*it+1], boneIds[4*it+2], boneIds[4*it+3])
            )
        }
        return AnimatedMesh(
            mName().dataString(),
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
class Bone(val id: Int, val name: String, val offsetMatrix: Matrix4f)
class VertexWeight(val boneId: Int, val vertexId: Int, val weight: Float)
fun AIMesh.retrieveBonesAndWeights(boneList: MutableList<Bone>): Pair<MutableList<Int>, MutableList<Float>> {
    val boneIds = mutableListOf<Int>()
    val weights = mutableListOf<Float>()
    val weightSet: MutableMap<Int, ArrayList<VertexWeight>> = HashMap()
    val numBones = mNumBones()
    val aiBones = mBones()
    for (i in 0 until numBones) {
        val aiBone = AIBone.create(aiBones!![i])
        val id = boneList.size
        val bone = Bone(id, aiBone.mName().dataString(), aiBone.mOffsetMatrix().toMatrix())
        boneList.add(bone)
        val numWeights = aiBone.mNumWeights()
        val aiWeights = aiBone.mWeights()
        for (j in 0 until numWeights) {
            val aiWeight = aiWeights[j]
            val vw = VertexWeight(bone.id, aiWeight.mVertexId(), aiWeight.mWeight())
            var vertexWeightList: ArrayList<VertexWeight>? = weightSet[vw.vertexId]
            if (vertexWeightList == null) {
                vertexWeightList = ArrayList()
                weightSet[vw.vertexId] = vertexWeightList
            }
            vertexWeightList.add(vw)
        }
    }
    val numVertices = mNumVertices()
    for (i in 0 until numVertices) {
        val vertexWeightList: List<VertexWeight> = weightSet[i]!!
        val size = vertexWeightList.size
        for (j in 0 until Mesh.MAX_WEIGHTS) {
            if (j < size) {
                val vw: VertexWeight = vertexWeightList[j]
                weights.add(vw.weight)
                boneIds.add(vw.boneId)
            } else {
                weights.add(0.0f)
                boneIds.add(0)
            }
        }
    }
    return Pair(boneIds, weights)
}


fun AIMatrix4x4.toMatrix(): Matrix4f {
    val result = Matrix4f()
    result.m00(a1())
    result.m10(a2())
    result.m20(a3())
    result.m30(a4())
    result.m01(b1())
    result.m11(b2())
    result.m21(b3())
    result.m31(b4())
    result.m02(c1())
    result.m12(c2())
    result.m22(c3())
    result.m32(c4())
    result.m03(d1())
    result.m13(d2())
    result.m23(d3())
    result.m33(d4())
    return result
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