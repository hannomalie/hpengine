package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.log.ConsoleLogger
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import org.joml.Vector2f
import org.joml.Vector3f
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.scene.Vertex

import java.io.*
import java.lang.IllegalArgumentException
import java.util.*
import java.util.logging.Level

import java.lang.Integer.parseInt
import java.util.concurrent.CompletableFuture

class OBJLoader {

    private var currentState: State? = null

    enum class State {
        READING_VERTEX,
        READING_UV,
        READING_NORMAL,
        READING_FACE,
        READING_MATERIALLIB
    }

    private fun removeEmpties(`in`: Array<String>): Array<String> {
        val inList = ArrayList(Arrays.asList(*`in`))
        for (i in inList.indices) {
            val entry = inList[i]
            if (entry.isEmpty()) {
                inList.removeAt(i)
            }
        }
        return inList.toTypedArray()
    }

    fun parseFloat(line: String): Float {
        return java.lang.Float.valueOf(line)
    }

    fun parseVertex(line: String): Vector3f {
        val xyz = removeEmpties(line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray())
        val x = java.lang.Float.valueOf(xyz[1])
        val y = java.lang.Float.valueOf(xyz[2])
        val z = java.lang.Float.valueOf(xyz[3])
        return Vector3f(x, y, z)
    }

    fun parseTexCoords(line: String): Vector2f {
        val xyz = removeEmpties(line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray())
        val x = java.lang.Float.valueOf(xyz[1])
        val y = java.lang.Float.valueOf(xyz[2])
        return Vector2f(x, y)
    }

    fun parseNormal(line: String): Vector3f {
        val xyz = removeEmpties(line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray())
        val x = java.lang.Float.valueOf(xyz[1])
        val y = java.lang.Float.valueOf(xyz[2])
        val z = java.lang.Float.valueOf(xyz[3])
        return Vector3f(x, y, z)
    }

    @Throws(Exception::class)
    fun parseFace(line: String): Face {
        val faceIndices = line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()

        val firstTriple = faceIndices[1].split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
        val secondTriple = faceIndices[2].split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
        val thirdTriple = faceIndices[3].split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
        val vertexIndices = intArrayOf(parseInt(firstTriple[0]), parseInt(secondTriple[0]), parseInt(thirdTriple[0]))
        var texCoordsIndices: IntArray
        try {
            texCoordsIndices = intArrayOf(parseInt(firstTriple[1]), parseInt(secondTriple[1]), parseInt(thirdTriple[1]))
        } catch (e: Exception) {
            texCoordsIndices = intArrayOf(-1, -1, -1)
        }

        val normalIndices = intArrayOf(parseInt(firstTriple[2]), parseInt(secondTriple[2]), parseInt(thirdTriple[2]))

        return Face(vertexIndices, texCoordsIndices, normalIndices)
    }

    @Throws(Exception::class)
    fun loadTexturedModel(materialManager: MaterialManager, f: File): StaticModel<Vertex> {
        if(!f.isFile) {
            throw IllegalArgumentException("File does not exist: ${f.absolutePath}")
        }
        val reader = BufferedReader(FileReader(f))
        val resultModel = StaticModel<Vertex>(f.path)

        var currentMesh: StaticMesh? = null
        var currentMaterial: SimpleMaterial? = null

        var usemtlCounter = 0

        var line: String? = reader.readLine()
        while (line != null) {

            val tokens = line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()

            var firstToken = ""
            if (tokens.isNotEmpty()) {
                firstToken = tokens[0]
            }
            if ("mtllib" == firstToken) {
                val materialLib = parseMaterialLib(materialManager.textureManager, line, f)
                materialManager.putAll(materialLib)
            } else if ("usemtl" == firstToken) {
                val materialName = line.replace("usemtl ", "")
                currentMaterial = materialManager.getMaterial(materialName)
                if (currentMaterial == null) {
                    LOGGER.log(Level.INFO, "No material found!!!")
                    currentMaterial = materialManager.defaultMaterial
                }
                usemtlCounter++
            } else if ("o" == firstToken || "g" == firstToken || line.startsWith("# object ")) {
                currentMesh = newMeshHelper(resultModel.getVertices(), resultModel.getTexCoords(), resultModel.getNormals(), line, line.replace("o ", "").replace("# object ", "").replace("g ", ""))
                resultModel.addMesh(currentMesh)
                usemtlCounter = 0
            } else if ("v" == firstToken) {
                currentState = State.READING_VERTEX
                resultModel.addVertex(parseVertex(line))
            } else if ("vt" == firstToken) {
                currentState = State.READING_UV
                resultModel.addTexCoords(parseTexCoords(line))
            } else if ("vn" == firstToken) {
                currentState = State.READING_NORMAL
                resultModel.addNormal(parseVertex(line))
            } else if ("f" == firstToken) {
                currentState = State.READING_FACE
                if (usemtlCounter > 1) {
                    currentMesh = newMeshHelper(resultModel.getVertices(), resultModel.getTexCoords(), resultModel.getNormals(), line, currentMesh!!.name + Random().nextInt())
                    resultModel.addMesh(currentMesh)
                    usemtlCounter = 0
                }
                currentMesh!!.material = currentMaterial
                currentMesh.indexedFaces.add(parseFace(line))
            }

            line = reader.readLine()
        }
        reader.close()

        resultModel.init(materialManager)
        return resultModel
    }

    private fun newMeshHelper(vertices: ArrayList<Vector3f>, texCoords: ArrayList<Vector2f>,
                              normals: ArrayList<Vector3f>, line: String, name: String): StaticMesh {
        val mesh = StaticMesh(line, vertices, texCoords, normals)
        mesh.name = name
        return mesh
    }


    private fun parseMaterialLib(textureManager: TextureManager, line: String, f: File): Map<String, MaterialInfo> {
        val materials = HashMap<String, MaterialInfo>()
        val twoStrings = line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()

        val fileName = twoStrings[1]
        val path = f.parent + "/"
        val finalPath = path + fileName

        var materialFileReader: BufferedReader? = null
        try {
            materialFileReader = BufferedReader(FileReader(finalPath))
        } catch (e1: FileNotFoundException) {
            e1.printStackTrace()
        }

        val parseMaterialName = ""
        var currentMaterialInfo: MaterialInfo? = null// = new SimpleMaterialInfo();

        val textureJobs: MutableList<CompletableFuture<Triple<SimpleMaterial.MAP, MaterialInfo, Texture<TextureDimension2D>>>> = mutableListOf()

        var materialLine: String?  = materialFileReader!!.readLine()
        try {
            while (materialLine != null) {

                val tokens = materialLine.split(" ")//.dropLastWhile { it.isEmpty() }.toTypedArray()
                val firstToken = if(tokens.isEmpty()) "" else tokens[0]
                val rest = if(tokens.size < 2) "" else (1 until tokens.size).map {tokens[it] }.reduce({left, right -> left + right}) // TODO: Use proper reduce and remove if

                if (firstToken.startsWith("#")) {
                    materialLine = materialFileReader.readLine()
                    continue
                } else if ("newmtl" == firstToken) {
                    val name = materialLine.replace("newmtl ", "")
                    currentMaterialInfo = SimpleMaterialInfo(name)

                } else if ("map_Kd" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.DIFFUSE, File(f.parentFile.absolutePath + "/" + map))
//                    textureJobs += addHelperXXX(textureManager, currentMaterialInfo!!, path, map, SimpleMaterial.MAP.DIFFUSE)

                } else if ("map_Ka" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.OCCLUSION, File(f.parentFile.absolutePath + "/" + map))
//                    textureJobs += addHelperXXX(textureManager, currentMaterialInfo!!, path, map, SimpleMaterial.MAP.OCCLUSION)

                } else if ("map_Disp" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.SPECULAR, File(f.parentFile.absolutePath + "/" + map))

                } else if ("map_Ks" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.SPECULAR, File(f.parentFile.absolutePath + "/" + map))
//                    textureJobs += addHelperXXX(textureManager, currentMaterialInfo!!, path, map, SimpleMaterial.MAP.SPECULAR)

                } else if ("map_Ns" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.ROUGHNESS, File(f.parentFile.absolutePath + "/" + map))
//                    textureJobs += addHelperXXX(textureManager, currentMaterialInfo!!, path, map, SimpleMaterial.MAP.ROUGHNESS)

                } else if ("map_bump" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.NORMAL, File(f.parentFile.absolutePath + "/" + map))

                } else if ("bump" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.NORMAL, File(f.parentFile.absolutePath + "/" + map))

                } else if ("map_d" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.NORMAL, File(f.parentFile.absolutePath + "/" + map))
//                    textureJobs += addHelperXXX(textureManager, currentMaterialInfo!!, path, map, SimpleMaterial.MAP.NORMAL)

                } else if ("map_Kh" == firstToken) {
                    val map = rest
                    currentMaterialInfo = addHelper(textureManager, currentMaterialInfo, path, map, SimpleMaterial.MAP.HEIGHT, File(f.parentFile.absolutePath + "/" + map))

                } else if ("Kd" == firstToken) {
                    currentMaterialInfo = currentMaterialInfo!!.copyXXX(diffuse = parseVertex(materialLine))
                } else if ("Kr" == firstToken) {
                    val roughness = rest
                    currentMaterialInfo = currentMaterialInfo!!.copyXXX(roughness = parseFloat(roughness))
                } else if ("Ks" == firstToken) {
                    val specular = materialLine
                    //currentMaterialInfo.specular = parseVertex(specular);
                    // Physically based tmaterials translate specular to roughness
                    //			    	  currentMaterialInfo.roughness = 1-parseVertex(specular).x;
                } else if ("Ns" == firstToken) {
                    val specularCoefficient = rest
                    //			    	  currentMaterialInfo.roughness = 1-(parseFloat(specularCoefficient)/1000 + 1);
                    //			    	  currentMaterialInfo.specularCoefficient = parseFloat(specularCoefficient);
                }
                // TODO: TRANSPARENCY with "d" and "Tr"
                if(currentMaterialInfo != null) {
                    materials[currentMaterialInfo.name] = currentMaterialInfo
                }
                materialLine = materialFileReader.readLine()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

//        textureJobs.forEach {
//            val result = it.get()
//            materials[result.second.name] = materials[result.second.name]!!.put(result.first, result.third)
//        }
        return materials
    }

    private fun addHelper(textureManager: TextureManager, currentMaterialInfo: MaterialInfo?, path: String, name: String, map: SimpleMaterial.MAP, file: File): MaterialInfo {
        return currentMaterialInfo!!.put(map, textureManager.getTexture(path + name, map === SimpleMaterial.MAP.DIFFUSE, file))
    }
    private fun addHelperXXX(textureManager: TextureManager, currentMaterialInfo: MaterialInfo, path: String, name: String, map: SimpleMaterial.MAP): CompletableFuture<Triple<SimpleMaterial.MAP, MaterialInfo, Texture<TextureDimension2D>>> {
        return CompletableFuture.supplyAsync {
            Triple(map, currentMaterialInfo, textureManager.getTexture(path + name, map === SimpleMaterial.MAP.DIFFUSE, Config.getInstance().directoryManager.gameDir))
        }
    }

    private fun parseName(line: String, mesh: StaticMesh) {
        val name = line.replace("o ", "")
        mesh.name = name
    }

    companion object {
        private val LOGGER = ConsoleLogger.getLogger()

        private fun asFloats(v: Vector3f): FloatArray {
            return floatArrayOf(v.x, v.y, v.z)
        }
    }
}
