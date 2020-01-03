package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeRenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.mipmapCount
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.struct.copyTo
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GL45
import java.io.File
import java.nio.FloatBuffer

class EnvironmentProbeExtension(val engineContext: EngineContext<OpenGl>) : RenderExtension<OpenGl> {

    private var renderedInCycle: Long = -1
    val probeRenderer = ProbeRenderer(engineContext)
    val evaluateProbeProgram = engineContext.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "evaluate_probe.glsl")

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        val entityAdded = renderState.entitiesState.entityAddedInCycle > renderedInCycle
        if (engineContext.config.debug.reRenderProbes || entityAdded) {
            probeRenderer.renderProbes(renderState, entityAdded)
            engineContext.config.debug.reRenderProbes = false
            renderedInCycle = renderState.cycle
        }
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val gBuffer = engineContext.deferredRenderingBuffer
        val gpuContext = engineContext.gpuContext
        gpuContext.disable(GlCap.DEPTH_TEST)
        evaluateProbeProgram.use()

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, gBuffer.positionMap)
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
        gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, gBuffer.motionMap)
        gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()

        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("timeGpu", System.currentTimeMillis().toInt())
        evaluateProbeProgram.setUniform("probeDimensions", probeRenderer.probeDimensions)
        val sceneCenter = Vector3f(probeRenderer.sceneMin).add(Vector3f(probeRenderer.sceneMax).sub(probeRenderer.sceneMin).mul(0.5f))
        evaluateProbeProgram.setUniform("sceneCenter", sceneCenter)
        evaluateProbeProgram.setUniform("sceneMin", probeRenderer.sceneMin)
        evaluateProbeProgram.setUniform("probesPerDimension", probeRenderer.probesPerDimensionFloat)

        evaluateProbeProgram.setUniform("probeCount", probeRenderer.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderer.probePositionsStructBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(5, probeRenderer.probeAmbientCubeValues)
        gpuContext.fullscreenBuffer.draw()

    }
}

class ProbeRenderer(private val engine: EngineContext<OpenGl>) {
    val sceneMin = Vector3f(-100f, -100f, -100f)
    val sceneMax = Vector3f(100f, 100f, 100f)
    val probesPerDimension = Vector3i(14, 4, 14)
    val probesPerDimensionFloat = Vector3f(probesPerDimension.x.toFloat(), probesPerDimension.y.toFloat(), probesPerDimension.z.toFloat())
    val probesPerDimensionHalfFloat = Vector3f(probesPerDimensionFloat).div(2f)
    val probesPerDimensionHalf = Vector3i(probesPerDimensionHalfFloat.x.toInt(), probesPerDimensionHalfFloat.y.toInt(), probesPerDimensionHalfFloat.z.toInt())
    val probeDimensions: Vector3f
        get() = Vector3f(sceneMax).sub(sceneMin).div(probesPerDimensionFloat)
    val probeDimensionsHalf: Vector3f
        get() = probeDimensions.mul(0.5f)
    val probeCount = probesPerDimension.x * probesPerDimension.y * probesPerDimension.z
    val probeResolution = 16
    val probePositions = mutableListOf<Vector3f>()
    val probePositionsStructBuffer = engine.gpuContext.calculate {
        PersistentMappedStructBuffer(probeCount, engine.gpuContext, { HpVector4f() })
    }
    val probeAmbientCubeValues = engine.gpuContext.calculate {
        PersistentMappedStructBuffer(probeCount * 6, engine.gpuContext, { HpVector4f() })
    }
    val probeAmbientCubeValuesOld = engine.gpuContext.calculate {
        PersistentMappedStructBuffer(probeCount * 6, engine.gpuContext, { HpVector4f() })
    }

    init {
        engine.gpuContext.execute("EnableCubeMapSeamlessFiltering") {
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
                    val resultingPosition = Vector3f(x * probeDimensions.x, y * probeDimensions.y, z * probeDimensions.z)
                    probePositions.add(resultingPosition.add(offset))
                }
            }
        }
        probePositions.mapIndexed { i, position ->
            probePositionsStructBuffer[i].set(position)
        }
    }


    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram: Program = engine.programManager.getProgram(
            getShaderSource(File(Shader.directory + "pointlight_shadow_cubemap_vertex.glsl")),
            getShaderSource(File(Shader.directory + "environmentprobe_cube_fragment.glsl")),
            getShaderSource(File(Shader.directory + "pointlight_shadow_cubemap_geometry.glsl")))
    var cubeMapRenderTarget = CubeMapRenderTarget(
            engine.backend,
            CubeRenderTargetBuilder(engine).add(
                ColorAttachmentDefinition("Probes", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
            )
            .setWidth(probeResolution)
            .setHeight(probeResolution)
    ).apply {
        engine.gpuContext.register(this)
    }

    fun renderProbes(renderState: RenderState, entityWasAdded: Boolean) {
        if (sceneMin != renderState.sceneMin || sceneMax != renderState.sceneMax) {
            sceneMin.set(renderState.sceneMin)
            sceneMax.set(renderState.sceneMax)
            initProbePositions()
        }
        probeAmbientCubeValues.copyTo(probeAmbientCubeValuesOld)

        val gpuContext = engine.gpuContext

        profiled("Probes") {

            gpuContext.depthMask(true)
            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(engine.gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, probeResolution, probeResolution)

            for (probeIndex in 0 until probeCount) {
                gpuContext.clearDepthBuffer()

                val skyBox = engine.materialManager.skyboxMaterial.materialInfo.maps[SimpleMaterial.MAP.ENVIRONMENT]
                        ?: engine.textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform("lightIndex", 0) // We don't use layered rendering with cubmap arrays anymore
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(5, probeAmbientCubeValuesOld)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.setUniform("sceneMin", sceneMin)
                pointCubeShadowPassProgram.setUniform("probesPerDimension", probesPerDimensionFloat)

                if(!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
                }
                engine.gpuContext.bindTexture(8, skyBox)
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
                    pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
                    pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
                }

                profiled("Probe entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(engine.gpuContext, batch.materialInfo.maps)
                        draw(renderState.vertexIndexBufferStatic.vertexBuffer,
                                renderState.vertexIndexBufferStatic.indexBuffer,
                                batch, pointCubeShadowPassProgram, false, false)
                    }
                }
                val cubeMap = cubeMapRenderTarget.textures.first() as CubeMap
                engine.textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMap.id)
                val floatArrayOf = (0 until 6*4).map { 0f }.toFloatArray()
                GL45.glGetTextureSubImage(cubeMap.id, cubeMap.mipmapCount-1,
                        0, 0, 0, 1, 1, 6, GL_RGBA, GL_FLOAT, floatArrayOf)
                val ambientCubeValues = floatArrayOf.toList().windowed(4, 4) {
                    Vector3f(it[0], it[1], it[2])
                }
                val baseProbeIndex = 6 * probeIndex
                for(faceIndex in 0 until 6) {
                    probeAmbientCubeValues[baseProbeIndex+faceIndex].set(ambientCubeValues[faceIndex])
                }
            }
        }
    }
}
