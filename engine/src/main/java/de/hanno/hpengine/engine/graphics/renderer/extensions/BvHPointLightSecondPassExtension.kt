package de.hanno.hpengine.engine.graphics.renderer.extensions

import com.dreizak.miniball.highdim.Miniball
import com.dreizak.miniball.model.ArrayPointSet
import com.dreizak.miniball.model.PointSet
import com.dreizak.miniball.model.PointSetUtils
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import kotlin.math.max

class BvhNodeGpu: Struct() {
    val positionRadius by HpVector4f()
    val missPointer by IntStruct()
    val dummy0 by IntStruct()
    val dummy1 by IntStruct()
    val dummy2 by IntStruct()
    val color by HpVector3f()
    val dummy3 by IntStruct()
}
typealias BoundingSphere = Vector4f
typealias Bvh = BvhNode
sealed class BvhNode(val boundingSphere: BoundingSphere) {
    var parent: BvhNode? = null
    class Inner(boundingSphere: BoundingSphere): BvhNode(boundingSphere) {
        val children = mutableListOf<BvhNode>()
        fun add(child: BvhNode) {
            child.parent = this
            children.add(child)
        }
    }
    class Leaf(boundingSphere: BoundingSphere, val color: Vector4f): BvhNode(boundingSphere)
}
fun MutableList<BvhNode.Leaf>.clustersOfN(n: Int = 4): List<BvhNode.Inner> {
    val result = mutableListOf<BvhNode.Inner>()
    while(isNotEmpty()) {
        val first = first()
        remove(first)
        val nearest = asSequence().sortedBy { first.boundingSphere.xyz.distance(it.boundingSphere.xyz) }.take(n-1).toList()
        removeAll(nearest)
        val nearestAndSelf = nearest + first
        val arrayPointSet = ArrayPointSet(3, 6 * nearestAndSelf.size).apply {
            nearestAndSelf.forEachIndexed { index, bvhNode ->
                val min = bvhNode.boundingSphere.xyz.sub(Vector3f(bvhNode.boundingSphere.w))
                val max = bvhNode.boundingSphere.xyz.add(Vector3f(bvhNode.boundingSphere.w))
                set(index, 0, min.x.toDouble())
                set(index, 1, min.y.toDouble())
                set(index, 2, min.z.toDouble())
                set(index, 3, max.x.toDouble())
                set(index, 4, max.y.toDouble())
                set(index, 5, max.z.toDouble())
            }
        }
        val enclosingSphere = Miniball(arrayPointSet).run {
            Vector4f(center()[0].toFloat(), center()[1].toFloat(), center()[2].toFloat(), radius().toFloat())
        }
        val innerNode = BvhNode.Inner(enclosingSphere).apply {
            nearestAndSelf.forEach {
                this@apply.add(it)
            }
        }
        result.add(innerNode)
    }
    return result
}

val Vector4f.xyz: Vector3f
    get() = Vector3f(x, y, z)

class BvHPointLightSecondPassExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val gpuContext = engineContext.gpuContext
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer

    private val secondPassPointBvhComputeProgram = engineContext.programManager.getComputeProgram("second_pass_point_trivial_bvh_compute.glsl")

    private val bvh = PersistentMappedStructBuffer(0, engineContext.gpuContext, { BvhNodeGpu() })

    fun Vector4f.set(other: Vector3f) {
        x = other.x
        y = other.y
        z = other.z
    }
    private var bvhReconstructedInCycle = -1L
    private var nodeCount = 0
    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        if (renderState.lightState.pointLights.isEmpty()) {
            return
        }
        if(bvhReconstructedInCycle < renderState.pointLightMovedInCycle) {
            bvhReconstructedInCycle = renderState.cycle
            val leafNodes = renderState.lightState.pointLights.map {
                BvhNode.Leaf(Vector4f().apply {
                    set(it.entity.position)
                    w = it.radius
                }, it.color)
            }.toMutableList()
            val innerNodes = leafNodes.clustersOfN()
            nodeCount = innerNodes.size + innerNodes.sumBy { it.children.size }
            bvh.enlarge(nodeCount)
            var counter = 0
            innerNodes.forEach { node ->
                bvh[counter].apply {
                    positionRadius.set(node.boundingSphere)
                    missPointer.value = counter + node.children.size + 1
                }
                counter++
                node.children.forEach { child ->
                    bvh[counter].apply {
                        positionRadius.set(child.boundingSphere)
                        missPointer.value = counter + 1
                        if(child is BvhNode.Leaf) {
                            color.set(child.color)
                        }
                    }
                    counter++
                }
            }
        }

//        bvh.enlarge(3)
//        val sceneExtents = Vector3f(renderState.sceneMax).sub(renderState.sceneMin)
//        val max = max(sceneExtents.x, max(sceneExtents.y, sceneExtents.z))
//        val sceneCenter = Vector3f(renderState.sceneMin).add(sceneExtents.mul(0.5f))
//        bvh[0].apply {
//            positionRadius.set(sceneCenter)
//            positionRadius.w = max
//            missPointer.value = 0
//        }
//        bvh[1].apply {
//            positionRadius.set(sceneCenter)
//            positionRadius.w = 25f
//            missPointer.value = 2
//            color.set(Vector4f(1f,0f,0f,0f))
//        }
//        bvh[2].apply {
//            positionRadius.set(Vector3f(5f, 5f, 5f))
//            positionRadius.w = 5f
//            missPointer.value = 3
//            color.set(Vector4f(0f,1f,0f,0f))
//        }
//        val nodeCount = 3

        profiled("Seconds pass PointLights BVH") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            renderState.lightState.pointLightShadowMapStrategy.bindTextures()
            // TODO: Add glbindimagetexture to openglcontext class
            GL42.glBindImageTexture(4, deferredRenderingBuffer.lightAccumulationMapOneId, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
            secondPassPointBvhComputeProgram.use()
            secondPassPointBvhComputeProgram.setUniform("nodeCount", nodeCount)
            secondPassPointBvhComputeProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
            secondPassPointBvhComputeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            secondPassPointBvhComputeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointBvhComputeProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(3, bvh)
            secondPassPointBvhComputeProgram.dispatchCompute(engineContext.config.width / 16, engineContext.config.height / 16, 1)
        }
    }
}