package de.hanno.hpengine.engine.graphics.renderer.extensions

import com.dreizak.miniball.highdim.Miniball
import com.dreizak.miniball.model.ArrayPointSet
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.batchAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleTransform
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File
import java.util.function.Consumer

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
        val arrayPointSet = ArrayPointSet(dimensions, pointsPerSphere * nearestAndSelf.size).apply {
            nearestAndSelf.forEachIndexed { sphereIndex, bvhNode ->
                val min = bvhNode.boundingSphere.xyz.sub(Vector3f(bvhNode.boundingSphere.w))
                val max = bvhNode.boundingSphere.xyz.add(Vector3f(bvhNode.boundingSphere.w))
                val points = AABB(min, max).getPoints()
                points.forEachIndexed { pointIndex, point ->
                    this.set(sphereIndex* pointsPerSphere + pointIndex, 0, point.x.toDouble())
                    this.set(sphereIndex* pointsPerSphere + pointIndex, 1, point.y.toDouble())
                    this.set(sphereIndex* pointsPerSphere + pointIndex, 2, point.z.toDouble())
                }
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

fun List<BvhNode.Leaf>.toTree(): BvhNode.Inner {
    var candidates: MutableList<out BvhNode> = toMutableList()
    while(candidates.size > 1) {
        candidates = candidates.clustersOfN()
    }
    return candidates.first() as BvhNode.Inner
}

val Vector4f.xyz: Vector3f
    get() = Vector3f(x, y, z)

class BvHPointLightSecondPassExtension(val engine: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val gpuContext = engine.gpuContext
    private val deferredRenderingBuffer = engine.deferredRenderingBuffer

    private val secondPassPointBvhComputeProgram = engine.programManager.getComputeProgram("second_pass_point_trivial_bvh_compute.glsl")

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }
    private val lineRenderer = LineRendererImpl(engine)
    private val sphereHolder = SphereHolder(engine)
    private val bvh = PersistentMappedStructBuffer(0, engine.gpuContext, { BvhNodeGpu() })

    fun Vector4f.set(other: Vector3f) {
        x = other.x
        y = other.y
        z = other.z
    }
    private var bvhReconstructedInCycle = -1L
    private var nodeCount = 0
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
                        this@apply.color.set(this@putToBufferHelper.color)
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
        if(bvhReconstructedInCycle < renderState.pointLightMovedInCycle) {
            bvhReconstructedInCycle = renderState.cycle
            val leafNodes = renderState.lightState.pointLights.map {
                BvhNode.Leaf(Vector4f().apply {
                    set(it.entity.position)
                    w = it.radius
                }, it.color)
            }.toMutableList()
            tree = leafNodes.toTree().apply {
                putToBuffer()
            }
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
            secondPassPointBvhComputeProgram.setUniform("screenWidth", engine.config.width.toFloat())
            secondPassPointBvhComputeProgram.setUniform("screenHeight", engine.config.height.toFloat())
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointBvhComputeProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(3, bvh)
            secondPassPointBvhComputeProgram.dispatchCompute(engine.config.width / 16, engine.config.height / 16, 1)
        }
    }

    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        tree?.run {
            val innerNodes = nodes.filterIsInstance<BvhNode.Inner>()
            innerNodes.forEach {
                val centerSelf = it.boundingSphere.xyz
                lineRenderer.batchAABBLines(
                    it.boundingSphere.xyz.sub(Vector3f(it.boundingSphere.w)),
                    it.boundingSphere.xyz.add(Vector3f(it.boundingSphere.w))
                )
                it.children.forEach {
                    lineRenderer.batchLine(centerSelf, it.boundingSphere.xyz)
                }
            }
            engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
//            engine.deferredRenderingBuffer.use(engine.gpuContext, false)
            engine.gpuContext.blend = false
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
            })
        }
    }
}

private class SphereHolder(val engine: EngineContext<OpenGl>,
                   private val sphereProgram: Program = engine.programManager.getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", Defines(Define.getDefine("PROGRAMMABLE_VERTEX_PULLING", true)))) : RenderSystem {

    private val materialManager: MaterialManager = engine.materialManager
    private val gpuContext = engine.gpuContext
    val sphereEntity = Entity("[Editor] Pivot")

    private val sphere = run {
        StaticModelLoader().load(File("assets/models/sphere.obj"), materialManager, engine.config.directories.engineDir)
    }

    private val sphereModelComponent = ModelComponent(sphereEntity, sphere, materialManager.defaultMaterial).apply {
        sphereEntity.addComponent(this)
    }
    private val sphereVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)

    private val vertexIndexOffsets = sphereVertexIndexBuffer.allocateForComponent(sphereModelComponent).apply {
        sphereModelComponent.putToBuffer(engine.gpuContext, sphereVertexIndexBuffer, this)
    }
    private val sphereCommand = DrawElementsIndirectCommand().apply {
        count = sphere.indices.size
        primCount = 1
        firstIndex = vertexIndexOffsets.indexOffset
        baseVertex = vertexIndexOffsets.vertexOffset
        baseInstance = 0
    }
    private val sphereRenderBatch = RenderBatch(entityBufferIndex = 0, isDrawLines = false,
            cameraWorldPosition = Vector3f(0f, 0f, 0f), drawElementsIndirectCommand = sphereCommand, isVisibleForCamera = true, update = Update.DYNAMIC,
            entityMinWorld = Vector3f(0f, 0f, 0f), entityMaxWorld = Vector3f(0f, 0f, 0f), centerWorld = Vector3f(),
            boundingSphereRadius = 1000f, animated = false, materialInfo = sphereModelComponent.material.materialInfo,
            entityIndex = sphereEntity.index, meshIndex = 0)

    private val transformBuffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }
    override fun render(result: DrawResult, state: RenderState) {
        render(state, emptyList(), true)
    }
    fun render(state: RenderState,
               nodes: List<BvhNode.Inner>,
               useDepthTest: Boolean = true,
               beforeDraw: (Program.() -> Unit)? = null) {

        val transformation = SimpleTransform().scale(1f).translate(Vector3f())
        if(useDepthTest) engine.gpuContext.enable(GlCap.DEPTH_TEST) else engine.gpuContext.disable(GlCap.DEPTH_TEST)
        engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", Vector3f(1f,0f,0f))
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)
        if (beforeDraw != null) { sphereProgram.beforeDraw() }

//        draw(sphereVertexIndexBuffer.vertexBuffer,
//                sphereVertexIndexBuffer.indexBuffer,
//                sphereRenderBatch, sphereProgram, false, false)

        nodes.forEach {
            val transformationPointLight = SimpleTransform().scale(it.boundingSphere.w).translate(it.boundingSphere.xyz)
            sphereProgram.setUniformAsMatrix4("modelMatrix", transformationPointLight.get(transformBuffer))
            sphereProgram.setUniform("diffuseColor", Vector3f(1f,0f,0f))

            draw(sphereVertexIndexBuffer.vertexBuffer,
                    sphereVertexIndexBuffer.indexBuffer,
                    sphereRenderBatch, sphereProgram, false, false)
        }
    }
}