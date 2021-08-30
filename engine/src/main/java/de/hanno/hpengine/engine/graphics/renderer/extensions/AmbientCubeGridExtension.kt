package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget2D
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toCubeMaps
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.model.texture.mipmapCount
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.struct.copyTo
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GL45
import java.nio.FloatBuffer

class AmbientCubeGridExtension(
    programManager: ProgramManager<OpenGl>,
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    textureManager: TextureManager,
    val deferredRenderingBuffer: DeferredRenderingBuffer
) : DeferredRenderExtension<OpenGl> {

    private val environmentProbeSphereHolder = SphereHolder(
        config,
        textureManager,
        gpuContext,
        programManager,
        programManager.run {
            getProgram(
                config.EngineAsset("shaders/mvp_vertex.glsl").toCodeSource(),
                config.EngineAsset("shaders/environmentprobe_color_fragment.glsl").toCodeSource()
            )
        },
        deferredRenderingBuffer.finalBuffer
    )

    val sphereProgram: Program<Uniforms> = config.run {
        programManager.getProgram(
            EngineAsset("shaders/mvp_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/simple_color_fragment.glsl").toCodeSource()
        )
    }

    private var renderedInCycle: Long = -1
    val probeRenderer = ProbeRenderer(gpuContext, config, programManager, textureManager)
    val evaluateProbeProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/evaluate_probe.glsl").toCodeSource(),
        Uniforms.Empty
    )

    private var renderCounter = 0
    private val probesPerFrame = 12.apply {
        require(probeRenderer.probeCount % this == 0) { "probecount has to be devidable by probesperframe" }
    }

    override fun renderFirstPass(
        backend: Backend<OpenGl>,
        gpuContext: GpuContext<OpenGl>,
        firstPassResult: FirstPassResult,
        renderState: RenderState
    ) {
        val entityAdded = renderState.entitiesState.entityAddedInCycle > renderedInCycle
        val needsRerender = config.debug.reRenderProbes || entityAdded
        if (needsRerender) {
            renderCounter = 0
            config.debug.reRenderProbes = false
            renderedInCycle = renderState.cycle
        }
        if (renderCounter < probeRenderer.probeCount - probesPerFrame) {
            probeRenderer.renderProbes(renderState, renderCounter, probesPerFrame)
            renderCounter += probesPerFrame
        }
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val gBuffer = deferredRenderingBuffer
        val gpuContext = gpuContext
        gpuContext.disable(GlCap.DEPTH_TEST)
        evaluateProbeProgram.use()

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, gBuffer.positionMap)
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
        gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, gBuffer.motionMap)
        gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()

        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())
        evaluateProbeProgram.setUniform("probeDimensions", probeRenderer.probeDimensions)
        val sceneCenter =
            Vector3f(probeRenderer.sceneMin).add(Vector3f(probeRenderer.sceneMax).sub(probeRenderer.sceneMin).mul(0.5f))
        evaluateProbeProgram.setUniform("sceneCenter", sceneCenter)
        evaluateProbeProgram.setUniform("sceneMin", probeRenderer.sceneMin)
        evaluateProbeProgram.setUniform("probesPerDimension", probeRenderer.probesPerDimensionFloat)

        evaluateProbeProgram.setUniform("probeCount", probeRenderer.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderer.probePositionsStructBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(5, probeRenderer.probeAmbientCubeValues)
        gpuContext.fullscreenBuffer.draw()

    }

    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        if (config.debug.visualizeProbes) {
            gpuContext.depthMask = true
            gpuContext.disable(GlCap.BLEND)
            environmentProbeSphereHolder.render(renderState) {

                probeRenderer.probePositions.withIndex().forEach { (probeIndex, position) ->
                    val transformation = Transform().translate(position)
                    sphereProgram.setUniform(
                        "pointLightPositionWorld",
                        probeRenderer.probePositions[probeIndex]
                    )
                    sphereProgram.setUniform("probeIndex", probeIndex)
                    sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                    sphereProgram.setUniform("probeDimensions", probeRenderer.probeDimensions)
                    sphereProgram.bindShaderStorageBuffer(4, probeRenderer.probePositionsStructBuffer)
                    sphereProgram.bindShaderStorageBuffer(5, probeRenderer.probeAmbientCubeValues)

                    sphereVertexIndexBuffer.indexBuffer.draw(
                        sphereRenderBatch,
                        sphereProgram,
                        bindIndexBuffer = false
                    )
                }
            }
        }
    }
}

class ProbeRenderer(
    val gpuContext: GpuContext<OpenGl>,
    config: Config,
    programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager
) {
    val sceneMin = Vector3f(-100f, -100f, -100f)
    val sceneMax = Vector3f(100f, 100f, 100f)
    val probesPerDimension = Vector3i(10, 6, 10)
    val probesPerDimensionFloat =
        Vector3f(probesPerDimension.x.toFloat(), probesPerDimension.y.toFloat(), probesPerDimension.z.toFloat())
    val probesPerDimensionHalfFloat = Vector3f(probesPerDimensionFloat).div(2f)
    val probesPerDimensionHalf = Vector3i(
        probesPerDimensionHalfFloat.x.toInt(),
        probesPerDimensionHalfFloat.y.toInt(),
        probesPerDimensionHalfFloat.z.toInt()
    )
    val probeDimensions: Vector3f
        get() = Vector3f(sceneMax).sub(sceneMin).div(probesPerDimensionFloat)
    val probeDimensionsHalf: Vector3f
        get() = probeDimensions.mul(0.5f)
    val probeCount = probesPerDimension.x * probesPerDimension.y * probesPerDimension.z
    val probeResolution = 16
    val probePositions = mutableListOf<Vector3f>()
    val probePositionsStructBuffer = gpuContext.window.invoke {
        PersistentMappedStructBuffer(probeCount, gpuContext, { HpVector4f() })
    }
    val probeAmbientCubeValues = gpuContext.window.invoke {
        PersistentMappedStructBuffer(probeCount * 6, gpuContext, { HpVector4f() })
    }
    val probeAmbientCubeValuesOld = gpuContext.window.invoke {
        PersistentMappedStructBuffer(probeCount * 6, gpuContext, { HpVector4f() })
    }

    init {
        gpuContext.window.invoke {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
        initProbePositions()
    }

    private fun initProbePositions() {
        val sceneCenter = Vector3f(sceneMin).add(Vector3f(sceneMax).sub(sceneMin).mul(0.5f))
        val offset = probeDimensionsHalf.add(sceneCenter)
        probePositions.clear()
        for (x in -probesPerDimensionHalf.x until probesPerDimensionHalf.x) {
            for (y in -probesPerDimensionHalf.y until probesPerDimensionHalf.y) {
                for (z in -probesPerDimensionHalf.z until probesPerDimensionHalf.z) {
                    val resultingPosition =
                        Vector3f(x * probeDimensions.x, y * probeDimensions.y, z * probeDimensions.z)
                    probePositions.add(resultingPosition.add(offset))
                }
            }
        }
        probePositions.mapIndexed { i, position ->
            probePositionsStructBuffer[i].set(position)
        }
    }


    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/environmentprobe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapRenderTarget = RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(
            gpuContext = gpuContext,
            depthBuffer = DepthBuffer(
                CubeMap(
                    gpuContext,
                    TextureDimension(probeResolution, probeResolution),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT
                )
            )
        ),
        width = probeResolution,
        height = probeResolution,
        textures = listOf(
            ColorAttachmentDefinition("Probes", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
        ).toCubeMaps(gpuContext, probeResolution, probeResolution),
        name = "Probes"
    )

    fun renderProbes(renderState: RenderState, probeStartIndex: Int, probesPerFrame: Int) {
        if (sceneMin != renderState.sceneMin || sceneMax != renderState.sceneMax) {
            sceneMin.set(renderState.sceneMin)
            sceneMax.set(renderState.sceneMax)
            initProbePositions()
        }
        probeAmbientCubeValues.copyTo(probeAmbientCubeValuesOld)

        val gpuContext = gpuContext

        profiled("Probes") {

            gpuContext.depthMask = true
            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, probeResolution, probeResolution)

            for (probeIndex in probeStartIndex until (probeStartIndex + probesPerFrame)) {
                gpuContext.clearDepthBuffer()

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform(
                    "lightIndex",
                    0
                ) // We don't use layered rendering with cubmap arrays anymore
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(5, probeAmbientCubeValuesOld)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.setUniform("sceneMin", sceneMin)
                pointCubeShadowPassProgram.setUniform("probesPerDimension", probesPerDimensionFloat)

                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        GlTextureTarget.TEXTURE_2D,
                        renderState.directionalLightState[0].shadowMapId
                    )
                }
                gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePositions[probeIndex])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    pointCubeShadowPassProgram.setUniformAsMatrix4(
                        "viewMatrices[$floatBufferIndex]",
                        viewMatrices[floatBufferIndex]!!
                    )
                    pointCubeShadowPassProgram.setUniformAsMatrix4(
                        "projectionMatrices[$floatBufferIndex]",
                        projectionMatrices[floatBufferIndex]!!
                    )
                }

                profiled("Probe entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.materialInfo.maps)
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(
                            batch,
                            pointCubeShadowPassProgram
                        )
                    }
                }
                val cubeMap = cubeMapRenderTarget.textures.first() as CubeMap
                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMap.id)
                val floatArrayOf = (0 until 6 * 4).map { 0f }.toFloatArray()
                GL45.glGetTextureSubImage(
                    cubeMap.id, cubeMap.mipmapCount - 1,
                    0, 0, 0, 1, 1, 6, GL_RGBA, GL_FLOAT, floatArrayOf
                )
                val ambientCubeValues = floatArrayOf.toList().windowed(4, 4) {
                    Vector3f(it[0], it[1], it[2])
                }
                val baseProbeIndex = 6 * probeIndex
                for (faceIndex in 0 until 6) {
                    probeAmbientCubeValues[baseProbeIndex + faceIndex].set(ambientCubeValues[faceIndex])
                }
            }
        }
    }
}

// TODO: Move the same class from editor module to main module and remove this one
private class SphereHolder(
    val config: Config,
    val textureManager: TextureManager,
    val gpuContext: GpuContext<OpenGl>,
    val programManager: ProgramManager<OpenGl>,
    val sphereProgram: Program<Uniforms> = config.run {
        programManager.getProgram(
            EngineAsset("shaders/mvp_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/simple_color_fragment.glsl").toCodeSource()
        )
    },
    val targetBuffer: RenderTarget2D
) : RenderSystem {

    val sphereEntity = Entity("[Editor] Pivot")

    val sphere = run {
        StaticModelLoader().load("assets/models/sphere.obj", textureManager, config.directories.engineDir)
    }

    val sphereModelComponent = ModelComponent(sphereEntity, sphere, MaterialManager.createDefaultMaterial(config, textureManager)).apply {
        sphereEntity.addComponent(this)
    }
    val sphereVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10)

    val vertexIndexOffsets = sphereVertexIndexBuffer.allocateForComponent(sphereModelComponent).apply {
        sphereModelComponent.putToBuffer(sphereVertexIndexBuffer, this)
    }
    val sphereCommand = DrawElementsIndirectCommand().apply {
        count = sphere.indices.size
        primCount = 1
        firstIndex = vertexIndexOffsets.indexOffset
        baseVertex = vertexIndexOffsets.vertexOffset
        baseInstance = 0
    }
    val sphereRenderBatch = RenderBatch(
        entityBufferIndex = 0,
        isDrawLines = false,
        cameraWorldPosition = Vector3f(0f, 0f, 0f),
        drawElementsIndirectCommand = sphereCommand,
        isVisibleForCamera = true,
        update = Update.DYNAMIC,
        entityMinWorld = Vector3f(0f, 0f, 0f),
        entityMaxWorld = Vector3f(0f, 0f, 0f),
        centerWorld = Vector3f(),
        boundingSphereRadius = 1000f,
        animated = false,
        materialInfo = sphereModelComponent.material.materialInfo,
        entityIndex = sphereEntity.index,
        meshIndex = 0
    )

    val transformBuffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }

    override fun render(result: DrawResult, renderState: RenderState) {
        render(renderState, sphereEntity.transform.position, Vector3f(0f, 0f, 1f))
    }

    fun render(
        state: RenderState, spherePosition: Vector3f,
        color: Vector3f, useDepthTest: Boolean = true,
        beforeDraw: (Program<Uniforms>.() -> Unit)? = null
    ) {

        val scaling = (0.1f * sphereEntity.transform.position.distance(state.camera.getPosition())).coerceIn(0.5f, 1f)
        val transformation = Transform().scale(scaling).translate(spherePosition)
        if (useDepthTest) gpuContext.enable(GlCap.DEPTH_TEST) else gpuContext.disable(GlCap.DEPTH_TEST)
        targetBuffer.use(gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", color)
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)
        if (beforeDraw != null) {
            sphereProgram.beforeDraw()
        }

        sphereVertexIndexBuffer.indexBuffer.draw(sphereRenderBatch, sphereProgram)

    }

    fun render(
        state: RenderState, useDepthTest: Boolean = true,
        draw: (SphereHolder.(RenderState) -> Unit)
    ) {

        val transformation = Transform()
        if (useDepthTest) gpuContext.enable(GlCap.DEPTH_TEST) else gpuContext.disable(GlCap.DEPTH_TEST)
        gpuContext.cullFace = false
        targetBuffer.use(gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)

        draw(state)
    }
}
