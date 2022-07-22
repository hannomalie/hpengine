package de.hanno.hpengine.graphics.renderer.extensions

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.addAABBLines
import de.hanno.hpengine.graphics.renderer.addLine
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.transform.Transform
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

class BvhNodeGpu: Struct() {
    val positionRadius by de.hanno.hpengine.scene.HpVector4f()
    val missPointer by IntStruct()
    val lightIndex by IntStruct()
    val dummy0 by IntStruct()
    val dummy1 by IntStruct()
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
    class Leaf(boundingSphere: BoundingSphere, val lightIndex: Int): BvhNode(boundingSphere)
}

val BvhNode.nodes: List<BvhNode>
    get() = when(this) {
        is BvhNode.Inner -> mutableListOf(this) + children.flatMap { it.nodes }
        is BvhNode.Leaf -> mutableListOf(this)
    }
val Bvh.nodeCount: Int
    get() = when(this) {
        is BvhNode.Inner -> 1 + children.sumBy { it.nodeCount }
        is BvhNode.Leaf -> 1
    }
fun MutableList<out BvhNode>.clustersOfN(n: Int = 4): MutableList<BvhNode.Inner> {
    val result = mutableListOf<BvhNode.Inner>()
    while(isNotEmpty()) {
        val first = first()
        remove(first)
        val nearest = asSequence().sortedBy { first.boundingSphere.xyz.distance(it.boundingSphere.xyz) }.take(n-1).toList()
        removeAll(nearest)
        val nearestAndSelf = nearest + first
        val pointsPerSphere = 8
        val dimensions = 3
//        val arrayPointSet = ArrayPointSet(dimensions, pointsPerSphere * nearestAndSelf.size).apply {
//            nearestAndSelf.forEachIndexed { sphereIndex, bvhNode ->
//                val min = bvhNode.boundingSphere.xyz.sub(Vector3f(bvhNode.boundingSphere.w))
//                val max = bvhNode.boundingSphere.xyz.add(Vector3f(bvhNode.boundingSphere.w))
//                val points = AABB(min, max).getPoints()
//                points.forEachIndexed { pointIndex, point ->
//                    this.set(sphereIndex* pointsPerSphere + pointIndex, 0, point.x.toDouble())
//                    this.set(sphereIndex* pointsPerSphere + pointIndex, 1, point.y.toDouble())
//                    this.set(sphereIndex* pointsPerSphere + pointIndex, 2, point.z.toDouble())
//                }
//            }
//        }
//        val enclosingSphere = Miniball(arrayPointSet).run {
//            Vector4f(center()[0].toFloat(), center()[1].toFloat(), center()[2].toFloat(), radius().toFloat())
//        }
        val enclosingSphere = nearestAndSelf.map { bvhNode ->
            val min = bvhNode.boundingSphere.xyz.sub(Vector3f(bvhNode.boundingSphere.w))
            val max = bvhNode.boundingSphere.xyz.add(Vector3f(bvhNode.boundingSphere.w))
            AABB(min, max)
        }.getSurroundingAABB().let { Vector4f(it.center.x, it.center.y, it.center.z, it.halfExtents.get(it.halfExtents.maxComponent())) }

        val innerNode = BvhNode.Inner(enclosingSphere).apply {
            nearestAndSelf.forEach {
                this@apply.add(it)
            }
        }
        result.add(innerNode)
    }
    return result
}

fun List<BvhNode.Leaf>.toTree(): BvhNode.Inner {
    var candidates: MutableList<out BvhNode> = toMutableList()
    while(candidates.any { it is BvhNode.Leaf } || candidates.size > 1) {
        candidates = candidates.clustersOfN()
    }
    return candidates.first() as BvhNode.Inner
}

val Vector4f.xyz: Vector3f
    get() = Vector3f(x, y, z)

class BvHPointLightSecondPassExtension(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer
): DeferredRenderExtension<OpenGl> {
    private val lineVertices = PersistentMappedStructBuffer(100, gpuContext, { de.hanno.hpengine.scene.HpVector4f() })

    private val secondPassPointBvhComputeProgram = programManager.getComputeProgram(config.EngineAsset("shaders/second_pass_point_trivial_bvh_compute.glsl"))

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }
    val bvh = PersistentMappedStructBuffer(0, gpuContext, { BvhNodeGpu() })

    fun Vector4f.set(other: Vector3f) {
        x = other.x
        y = other.y
        z = other.z
    }
    private var bvhReconstructedInCycle = -1L
    var nodeCount = 0
    var tree: Bvh? = null

    private fun BvhNode.Inner.putToBuffer() {
        this@BvHPointLightSecondPassExtension.nodeCount = nodeCount
        bvh.enlarge(nodeCount)
        var counter = 0

        fun BvhNode.putToBufferHelper() {
            when(this) {
                is BvhNode.Inner -> {
                    bvh[counter].apply {
                        positionRadius.set(boundingSphere)
                        missPointer.value = counter + nodeCount
                    }
                    counter++
                    children.forEach { it.putToBufferHelper() }
                }
                is BvhNode.Leaf -> {
                    bvh[counter].apply {
                        positionRadius.set(boundingSphere)
                        missPointer.value = counter + 1
                        lightIndex.value = this@putToBufferHelper.lightIndex
                    }
                    counter++
                }
            }.let {}
        }
        putToBufferHelper()
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        if (renderState.lightState.pointLights.isEmpty()) {
            return
        }
        // TODO: Move this to update
        if(bvhReconstructedInCycle < renderState.pointLightMovedInCycle) {
//             TODO: Reimplement this
//            bvhReconstructedInCycle = renderState.cycle
//            val leafNodes = renderState.lightState.pointLights.mapIndexed { index, light ->
//                BvhNode.Leaf(Vector4f().apply {
//                    set(light.entity.transform.position)
//                    w = light.radius
//                }, index)
//            }.toMutableList()
//            tree = leafNodes.toTree().apply {
//                putToBuffer()
//            }
        }

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
            secondPassPointBvhComputeProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassPointBvhComputeProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointBvhComputeProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(3, bvh)
            secondPassPointBvhComputeProgram.dispatchCompute(config.width / 16, config.height / 16, 1)
        }
    }

    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        if(config.debug.drawBvhInnerNodes) {

            val linePoints = mutableListOf<Vector3fc>().apply {
                tree?.run {
                    val innerNodes = nodes.filterIsInstance<BvhNode.Inner>()
                    innerNodes.forEach {
                        val centerSelf = it.boundingSphere.xyz
                        addAABBLines(
                            it.boundingSphere.xyz.sub(Vector3f(it.boundingSphere.w)),
                            it.boundingSphere.xyz.add(Vector3f(it.boundingSphere.w))
                        )
                        it.children.forEach { child ->
                            addLine(centerSelf, child.boundingSphere.xyz)
                        }
                    }
                }
            }
            deferredRenderingBuffer.finalBuffer.use(gpuContext, false)
            gpuContext.blend = false
            drawLines(renderStateManager, programManager, lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
        }

    }
}
