package de.hanno.hpengine.model.loader.assimp

import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.model.animation.AnimatedFrame
import de.hanno.hpengine.model.AnimatedMesh
import de.hanno.hpengine.model.AnimatedModel
import de.hanno.hpengine.model.animation.Animation
import de.hanno.hpengine.model.Mesh
import de.hanno.hpengine.model.animation.Node
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.texture.Texture
import de.hanno.hpengine.model.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.AnimatedVertex
import org.joml.Matrix4f
import org.joml.Quaternionf
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
import java.io.File
import java.nio.IntBuffer
import java.nio.file.Path
import java.util.ArrayList
import kotlin.math.max

val defaultFlagsAnimated = Assimp.aiProcess_Triangulate + Assimp.aiProcess_JoinIdenticalVertices +
        Assimp.aiProcess_LimitBoneWeights + Assimp.aiProcess_GenBoundingBoxes + Assimp.aiProcess_GenNormals +
        Assimp.aiProcess_GenNormals

class AnimatedModelLoader(val flags: Int = defaultFlagsAnimated) {
    fun load(file: String, textureManager: OpenGLTextureManager, resourcesDir: AbstractDirectory): AnimatedModel {
        val path = resourcesDir.resolve(file).path.apply {
            require(File(this).exists()) { "File doesn't exist: $this" }
        }
        val aiScene = Assimp.aiImportFile(path, flags)
            ?: throw IllegalStateException("Cannot load model $file")
        val numMaterials: Int = aiScene.mNumMaterials()
        val aiMaterials: PointerBuffer? = aiScene.mMaterials()
        val materials = (0 until numMaterials).map { i ->
            val aiMaterial = AIMaterial.create(aiMaterials!![i])
            aiMaterial.processMaterial(
                Path.of(file).parent.toString(),
                resourcesDir,
                textureManager
            )
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

        return AnimatedModel(resourcesDir.resolve(file), meshes, animations)
    }

    private fun AIScene.processAnimations(
        boneList: List<Bone>, rootNode: Node,
        rootTransformation: Matrix4f
    ): Map<String, Animation> {
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
            val animation = Animation(aiAnimation.mName().dataString().takeUnless { it.isBlank() }
                ?: "Default",
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

    fun buildAnimationFrames(boneList: List<Bone>, rootNode: Node, rootTransformation: Matrix4f): List<AnimatedFrame> {
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

    private fun AIMaterial.processMaterial(
        texturesDir: String,
        resourcesDir: AbstractDirectory,
        textureManager: OpenGLTextureManager
    ): Material {
        fun AIMaterial.retrieveTexture(textureIdentifier: Int): Texture? {
            AIString.calloc().use { path ->
                Assimp.aiGetMaterialTexture(
                    this,
                    textureIdentifier,
                    0,
                    path,
                    null as IntBuffer?,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                val textPath = path.dataString()
                return if (textPath.isNotEmpty()) {
                    textureManager.getTexture("$texturesDir/$textPath", directory = resourcesDir)
                } else null
            }
        }

        val name = AIString.calloc()
        Assimp.aiGetMaterialString(this, Assimp.AI_MATKEY_NAME, Assimp.aiTextureType_NONE, 0, name)

        val colour = AIColor4D.create()
        var ambient = Vector4f()
        var result: Int =
            Assimp.aiGetMaterialColor(this, Assimp.AI_MATKEY_COLOR_EMISSIVE, Assimp.aiTextureType_NONE, 0, colour)
        if (result == 0) {
            ambient = Vector4f(colour.r(), colour.g(), colour.b(), colour.a())
        }
        var diffuse = Vector4f()
        result = Assimp.aiGetMaterialColor(this, Assimp.AI_MATKEY_COLOR_DIFFUSE, Assimp.aiTextureType_NONE, 0, colour)
        if (result == 0) {
            diffuse = Vector4f(colour.r(), colour.g(), colour.b(), colour.a())
        }
        val material = Material(
            name.dataString().ifEmpty { System.currentTimeMillis().toString().reversed().substring(0, 5) },
            ambient = max(max(ambient.x, ambient.y), ambient.z),
            diffuse = Vector3f(diffuse.x, diffuse.y, diffuse.z)
        )
        material.putIfNotNull(Material.MAP.DIFFUSE, retrieveTexture(Assimp.aiTextureType_DIFFUSE))
        val normalOrHeightMap =
            retrieveTexture(Assimp.aiTextureType_NORMALS) ?: retrieveTexture(Assimp.aiTextureType_HEIGHT)
        material.putIfNotNull(Material.MAP.NORMAL, normalOrHeightMap)
        material.putIfNotNull(Material.MAP.SPECULAR, retrieveTexture(Assimp.aiTextureType_SPECULAR))

        return material
    }

    private fun Material.putIfNotNull(map: Material.MAP, texture: Texture?) {
        if (texture != null) put(map, texture)
    }

    private fun AIMesh.processMesh(materials: List<Material>, bones: MutableList<Bone>): AnimatedMesh {

        val positions = retrievePositions()
        val normals = retrieveNormals()
        val texCoords = retrieveTexCoords()
        val indices = retrieveFaces()
        val aabb = retrieveAABB()
        val (boneIds, weights) = retrieveBonesAndWeights(bones)
        val materialIdx = mMaterialIndex()
        val material = if (materialIdx >= 0 && materialIdx < materials.size) {
            materials[materialIdx]
        } else {
            Material(mName().dataString() + "_material")
        }
        val vertices = positions.indices.map {
            AnimatedVertex(positions[it],
                texCoords[it],
                runCatching { normals[it] }.getOrElse { Vector3f(0f, 1f, 0f) },
                Vector4f(weights[4 * it + 0], weights[4 * it + 1], weights[4 * it + 2], weights[4 * it + 3]),
                Vector4i(boneIds[4 * it + 0], boneIds[4 * it + 1], boneIds[4 * it + 2], boneIds[4 * it + 3])
            )
        }
        return AnimatedMesh(
            mName().dataString(),
            vertices,
            indices,
            aabb,
            material
        )
    }
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