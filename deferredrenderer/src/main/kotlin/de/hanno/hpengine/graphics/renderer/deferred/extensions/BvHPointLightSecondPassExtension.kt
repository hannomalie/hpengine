package de.hanno.hpengine.graphics.renderer.deferred.extensions

import BvhNodeGpuImpl.Companion.type
import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.SizeInBytes

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.addAABBLines
import de.hanno.hpengine.graphics.renderer.addLine
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.StringBasedCodeSource
import de.hanno.hpengine.toCount
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.forIndex
import java.nio.ByteBuffer

interface BvhNodeGpu : Strukt {
    context(ByteBuffer) val positionRadius: Vector4fStrukt
    context(ByteBuffer) val missPointer: IntStrukt
    context(ByteBuffer) val lightIndex: IntStrukt
    context(ByteBuffer) val dummy0: IntStrukt
    context(ByteBuffer) val dummy1: IntStrukt

    companion object
}
typealias BoundingSphere = Vector4f
typealias Bvh = BvhNode

sealed class BvhNode(val boundingSphere: BoundingSphere) {
    var parent: BvhNode? = null

    class Inner(boundingSphere: BoundingSphere) : BvhNode(boundingSphere) {
        val children = mutableListOf<BvhNode>()
        fun add(child: BvhNode) {
            child.parent = this
            children.add(child)
        }
    }

    class Leaf(boundingSphere: BoundingSphere, var lightIndex: Int) : BvhNode(boundingSphere)
}

val BvhNode.nodes: List<BvhNode>
    get() = when (this) {
        is BvhNode.Inner -> mutableListOf(this) + children.flatMap { it.nodes }
        is BvhNode.Leaf -> mutableListOf(this)
    }
val Bvh.nodeCount: Int
    get() = when (this) {
        is BvhNode.Inner -> 1 + children.sumBy { it.nodeCount }
        is BvhNode.Leaf -> 1
    }

fun MutableList<out BvhNode>.clustersOfN(n: Int = 4): MutableList<BvhNode.Inner> {
    val result = mutableListOf<BvhNode.Inner>()
    while (isNotEmpty()) {
        val first = first()
        remove(first)
        val nearest =
            asSequence().sortedBy { first.boundingSphere.xyz.distance(it.boundingSphere.xyz) }.take(n - 1).toList()
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
        }.getSurroundingAABB()
            .let { Vector4f(it.center.x, it.center.y, it.center.z, it.halfExtents.get(it.halfExtents.maxComponent())) }

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
    while (candidates.any { it is BvhNode.Leaf } || candidates.size > 1) {
        candidates = candidates.clustersOfN()
    }
    return candidates.first() as BvhNode.Inner
}

val Vector4f.xyz: Vector3f
    get() = Vector3f(x, y, z)

@Single(binds = [BvHPointLightSecondPassExtension::class, DeferredRenderExtension::class])
class BvHPointLightSecondPassExtension(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val programManager: ProgramManager,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val pointLightSystem: PointLightSystem,
    private val materialSystem: MaterialSystem,
) : DeferredRenderExtension {
    private val lineVertices = graphicsApi.PersistentShaderStorageBuffer(100.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)

    private val secondPassPointBvhComputeProgram =
        programManager.getComputeProgram(config.EngineAsset("shaders/second_pass_point_trivial_bvh_compute.glsl"))

    private val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms(graphicsApi)
        getProgram(
            StringBasedCodeSource(
                "mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()
            ),
            StringBasedCodeSource(
                "simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()
            ), null, Defines(), uniforms
        )
    }

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }
    val bvh = graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(BvhNodeGpu.type.sizeInBytes)).typed(BvhNodeGpu.type)

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
        bvh.typedBuffer.enlarge(nodeCount)
        var counter = 0

        fun BvhNode.putToBufferHelper() {
            when (this) {
                is BvhNode.Inner -> {
                    bvh.typedBuffer.forIndex(counter) {
                        it.positionRadius.set(boundingSphere)
                        it.missPointer.value = counter + nodeCount
                    }
                    counter++
                    children.forEach { it.putToBufferHelper() }
                }
                is BvhNode.Leaf -> {
                    bvh.typedBuffer.forIndex(counter) {
                        it.positionRadius.set(boundingSphere)
                        it.missPointer.value = counter + 1
                        lightIndex = this@putToBufferHelper.lightIndex
                    }
                    counter++
                }
            }.let {}
        }
        putToBufferHelper()
    }

    override fun renderSecondPassFullScreen(renderState: RenderState) = graphicsApi.run {
        val pointLightState = renderState[pointLightStateHolder.lightState]
        if (pointLightState.pointLightCount == 0) {
            return
        }
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        // TODO: Move this to update
        val anyPointLightHasMoved = pointLightState.pointLightMovedInCycle.entries.any { bvhReconstructedInCycle <= it.value }
        if (anyPointLightHasMoved) {
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

            val camera = renderState[primaryCameraStateHolder.camera]

            val viewMatrix = camera.viewMatrixBuffer
            val projectionMatrix = camera.projectionMatrixBuffer

            graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            graphicsApi.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            graphicsApi.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            graphicsApi.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            graphicsApi.bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            graphicsApi.bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            pointLightSystem.shadowMapStrategy.bindTextures()
            graphicsApi.bindImageTexture(
                4,
                deferredRenderingBuffer.lightAccumulationMapOneId,
                0,
                false,
                0,
                Access.ReadWrite,
                InternalTextureFormat.RGBA16F
            )
            secondPassPointBvhComputeProgram.use()
            secondPassPointBvhComputeProgram.setUniform("nodeCount", nodeCount)
            secondPassPointBvhComputeProgram.setUniform("pointLightCount", pointLightState.pointLightCount)
            secondPassPointBvhComputeProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassPointBvhComputeProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointBvhComputeProgram.setUniform(
                "maxPointLightShadowmaps",
                PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS
            )
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(2, pointLightState.pointLightBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(3, bvh)
            secondPassPointBvhComputeProgram.dispatchCompute(config.width .toCount() / 16, config.height.toCount() / 16, 1.toCount())
        }
    }

    override fun renderEditor(renderState: RenderState) {
        if (config.debug.drawBvhInnerNodes) {

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
            deferredRenderingBuffer.finalBuffer.use(false)
            graphicsApi.blend = false
            val camera = renderState[primaryCameraStateHolder.camera]
            graphicsApi.drawLines(
                programManager,
                linesProgram,
                lineVertices,
                linePoints,
                viewMatrix = camera.viewMatrixBuffer,
                projectionMatrix = camera.projectionMatrixBuffer,
                color = Vector3f(1f, 0f, 0f)
            )
        }

    }
}
