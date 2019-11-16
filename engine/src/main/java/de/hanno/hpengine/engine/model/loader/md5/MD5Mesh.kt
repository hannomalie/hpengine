package de.hanno.hpengine.engine.model.loader.md5

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4i

import java.io.File
import java.util.ArrayList
import java.util.regex.Pattern

class MD5Mesh() : Mesh<AnimatedVertex> {
    override var compiledVertices: List<AnimatedVertex> = mutableListOf()
    var positionsArray: FloatArray = floatArrayOf()
    private var textCoordsArr: FloatArray = floatArrayOf()
    private var normalsArr: FloatArray = floatArrayOf()
    override var indexBufferValues: IntArray = intArrayOf()
    private var jointIndicesArr: IntArray = intArrayOf()
    private var weightsArr: FloatArray = floatArrayOf()

    var diffuseTexture: String? = null

    var vertices: ArrayList<MD5Vertex> = ArrayList()

    var triangles: ArrayList<MD5Triangle> = ArrayList()

    var weights: ArrayList<MD5Weight> = ArrayList()
    override var vertexBufferValues: FloatArray = floatArrayOf()
    private val indicesList = IntArrayList()
    override lateinit var material: Material
    override lateinit var name: String
    private var model: AnimatedModel? = null

    override val triangleCount: Int
        get() = 0
    internal var centerTemp = Vector3f()

    override val boundingSphereRadius: Float
        get() = model!!.boundingSphereRadius

    override val faces: List<StaticMesh.CompiledFace>
        get() = emptyList()

    constructor(positionsArr: FloatArray, textCoordsArr: FloatArray, normalsArr: FloatArray, indicesArr: IntArray, jointIndicesArr: IntArray, weightsArr: FloatArray) : this() {
        this.positionsArray = positionsArr
        this.textCoordsArr = textCoordsArr
        this.normalsArr = normalsArr
        this.indexBufferValues = indicesArr
        this.jointIndicesArr = jointIndicesArr
        this.weightsArr = weightsArr

        var counter = 0

        val vertexCount = positionsArr.size / 3
        vertexBufferValues = FloatArray(vertexCount * VALUES_PER_VERTEX)
        var i = 0
        while (i < vertexCount) {
            val currentIndex = i

            val vec3BaseIndex = 3 * currentIndex
            vertexBufferValues[counter++] = positionsArr[vec3BaseIndex]
            vertexBufferValues[counter++] = positionsArr[vec3BaseIndex + 1]
            vertexBufferValues[counter++] = positionsArr[vec3BaseIndex + 2]

            vertexBufferValues[counter++] = textCoordsArr[2 * currentIndex]
            vertexBufferValues[counter++] = textCoordsArr[2 * currentIndex + 1]

            vertexBufferValues[counter++] = normalsArr[vec3BaseIndex]
            vertexBufferValues[counter++] = normalsArr[vec3BaseIndex + 1]
            vertexBufferValues[counter++] = normalsArr[vec3BaseIndex + 2]
            i += 1

        }
    }

    constructor(positionsArr: FloatArray, textCoordsArr: FloatArray, normalsArr: FloatArray, indicesArr: IntArray, jointIndicesArr: IntArray, weightsArr: FloatArray, vertices: List<AnimCompiledVertex>) : this(positionsArr, textCoordsArr, normalsArr, indicesArr, jointIndicesArr, weightsArr) {
        this.compiledVertices = vertices.map { convert(it) }
    }

    private fun convert(input: AnimCompiledVertex): AnimatedVertex {
        val weights1 = FloatArray(4)
        for (i in 0..3) {
            weights1[i] = 0f
        }
        run {
            var i = 0
            while (i < input.weights.size && i < 4) {
                weights1[i] = input.weights[i]
                i++
            }
        }
        val weights = Vector4f(weights1[0], weights1[1], weights1[2], weights1[3])
        val jointIndices1 = IntArray(4)
        for (i in 0..3) {
            jointIndices1[i] = 0
        }
        var i = 0
        while (i < input.jointIndices.size && i < 4) {
            jointIndices1[i] = input.jointIndices[i]
            i++
        }
        val jointIndices = Vector4i(jointIndices1[0], jointIndices1[1], jointIndices1[2], jointIndices1[3])
        return AnimatedVertex(input.position, input.textCoords, input.normal, weights, jointIndices)
    }

    override fun toString(): String {
        val str = StringBuilder("mesh [" + System.lineSeparator())
        str.append("diffuseTexture: ").append(diffuseTexture).append(System.lineSeparator())

        str.append("vertices [").append(System.lineSeparator())
        for (vertex in vertices) {
            str.append(vertex).append(System.lineSeparator())
        }
        str.append("]").append(System.lineSeparator())

        str.append("triangles [").append(System.lineSeparator())
        for (triangle in triangles) {
            str.append(triangle).append(System.lineSeparator())
        }
        str.append("]").append(System.lineSeparator())

        str.append("weights [").append(System.lineSeparator())
        for (weight in weights) {
            str.append(weight).append(System.lineSeparator())
        }
        str.append("]").append(System.lineSeparator())

        return str.toString()
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        return model!!.getMinMax(transform)
    }

    override fun getCenter(entity: Entity): Vector3f {
        val component = entity.getComponent(ModelComponent::class.java)
        val animatedModel = component!!.model as AnimatedModel
        return entity.transformPosition(centerTemp.set(animatedModel.getCurrentBoundInfo(animatedModel.animationController.currentFrameIndex).getCenterWorld(entity)))
    }

    fun setModel(model: AnimatedModel) {
        this.model = model
    }

    class MD5Vertex {

        var index: Int = 0

        var textCoords: Vector2f? = null

        var startWeight: Int = 0

        var weightCount: Int = 0

        override fun toString(): String {
            return ("[index: " + index + ", textCoods: " + textCoords
                    + ", startWeight: " + startWeight + ", weightCount: " + weightCount + "]")
        }
    }

    class MD5Triangle {

        var index: Int = 0

        var vertex0: Int = 0

        var vertex1: Int = 0

        var vertex2: Int = 0

        override fun toString(): String {
            return ("[index: " + index + ", vertex0: " + vertex0
                    + ", vertex1: " + vertex1 + ", vertex2: " + vertex2 + "]")
        }
    }

    class MD5Weight {

        var index: Int = 0

        var jointIndex: Int = 0

        var bias: Float = 0.toFloat()

        var position: Vector3f? = null

        override fun toString(): String {
            return ("[index: " + index + ", jointIndex: " + jointIndex
                    + ", bias: " + bias + ", position: " + position + "]")
        }
    }

    companion object {

        private val PATTERN_SHADER = Pattern.compile("\\s*shader\\s*\\\"([^\\\"]+)\\\"")

        private val PATTERN_VERTEX = Pattern.compile("\\s*vert\\s*(\\d+)\\s*\\(\\s*("
                + MD5Utils.FLOAT_REGEXP + ")\\s*(" + MD5Utils.FLOAT_REGEXP + ")\\s*\\)\\s*(\\d+)\\s*(\\d+)")

        private val PATTERN_TRI = Pattern.compile("\\s*tri\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)")

        private val PATTERN_WEIGHT = Pattern.compile("\\s*weight\\s*(\\d+)\\s*(\\d+)\\s*" +
                "(" + MD5Utils.FLOAT_REGEXP + ")\\s*" + MD5Utils.VECTOR3_REGEXP)
        val VALUES_PER_VERTEX = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS)

        fun parse(modelFileBaseDir: File, meshBlock: List<String>): MD5Mesh {
            val mesh = MD5Mesh()
            val vertices = mesh.vertices
            val triangles = mesh.triangles
            val weights = mesh.weights

            for (line in meshBlock) {
                if (line.contains("shader")) {
                    val textureMatcher = PATTERN_SHADER.matcher(line)
                    if (textureMatcher.matches()) {
                        val textureFileName = textureMatcher.group(1)
                        mesh.diffuseTexture = modelFileBaseDir.absolutePath + "/" + textureFileName
                        mesh.name = textureFileName
                    }
                } else if (line.contains("vert")) {
                    val vertexMatcher = PATTERN_VERTEX.matcher(line)
                    if (vertexMatcher.matches()) {
                        val vertex = MD5Vertex()
                        vertex.index = Integer.parseInt(vertexMatcher.group(1))
                        val x = java.lang.Float.parseFloat(vertexMatcher.group(2))
                        val y = java.lang.Float.parseFloat(vertexMatcher.group(3))
                        vertex.textCoords = Vector2f(x, y)
                        vertex.startWeight = Integer.parseInt(vertexMatcher.group(4))
                        vertex.weightCount = Integer.parseInt(vertexMatcher.group(5))
                        vertices.add(vertex)
                    }
                } else if (line.contains("tri")) {
                    val triMatcher = PATTERN_TRI.matcher(line)
                    if (triMatcher.matches()) {
                        val triangle = MD5Triangle()
                        triangle.index = Integer.parseInt(triMatcher.group(1))
                        triangle.vertex0 = Integer.parseInt(triMatcher.group(2))
                        triangle.vertex1 = Integer.parseInt(triMatcher.group(4))
                        triangle.vertex2 = Integer.parseInt(triMatcher.group(3))
                        //                    triangle.setVertex0(Integer.parseInt(triMatcher.group(2)));
                        //                    triangle.setVertex1(Integer.parseInt(triMatcher.group(3)));
                        //                    triangle.setVertex2(Integer.parseInt(triMatcher.group(4)));
                        triangles.add(triangle)
                    }
                } else if (line.contains("weight")) {
                    val weightMatcher = PATTERN_WEIGHT.matcher(line)
                    if (weightMatcher.matches()) {
                        val weight = MD5Weight()
                        weight.index = Integer.parseInt(weightMatcher.group(1))
                        weight.jointIndex = Integer.parseInt(weightMatcher.group(2))
                        weight.bias = java.lang.Float.parseFloat(weightMatcher.group(3))
                        val x = java.lang.Float.parseFloat(weightMatcher.group(4))
                        val y = java.lang.Float.parseFloat(weightMatcher.group(5))
                        val z = java.lang.Float.parseFloat(weightMatcher.group(6))
                        weight.position = Vector3f(x, y, z)
                        weights.add(weight)
                    }
                }
            }

            return mesh
        }
    }
}
