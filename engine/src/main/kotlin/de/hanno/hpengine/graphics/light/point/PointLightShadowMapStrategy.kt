package de.hanno.hpengine.graphics.light.point


import InternalTextureFormat.DEPTH_COMPONENT24
import InternalTextureFormat.RGBA16F
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.graphics.constants.WrapMode.ClampToEdge
import de.hanno.hpengine.graphics.constants.WrapMode.Repeat
import de.hanno.hpengine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.math.OmniCamera
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.get
import java.nio.FloatBuffer
import kotlin.math.min

@Single
class CubeShadowMapStrategy(
    private val graphicsApi: GraphicsApi,
    config: Config,
    programManager: ProgramManager,
    private val pointLightStateHolder: PointLightStateHolder,
    private val movementSystem: EntityMovementSystem,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val materialSystem: MaterialSystem,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val renderStateContext: RenderStateContext,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val textureManager: TextureManagerBaseSystem,
) {
    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cube_fragment.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_geometry.glsl")),
        Defines(),
        Uniforms.Empty
    )

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        null,//config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("COLOR_OUTPUT_0", true)),
        StaticFirstPassUniforms(graphicsApi)
    )

    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesStatic]
        }
    }
    val cubeMapArray = graphicsApi.CubeMapArray(
        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS),
        TextureFilterConfig(MinFilter.NEAREST),
        RGBA16F,
        Repeat
    )
    val depthCubeMapArray = graphicsApi.CubeMapArray(
        TextureDimension(
            cubeMapArray.dimension.width,
            cubeMapArray.dimension.height,
            cubeMapArray.dimension.depth
        ),
        TextureFilterConfig(minFilter = MinFilter.NEAREST),
        internalFormat = DEPTH_COMPONENT24,
        wrapMode = ClampToEdge
    )
    val pointLightDepthMapsArrayCube = depthCubeMapArray.id
    var cubemapArrayRenderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(graphicsApi.createDepthBuffer(depthCubeMapArray)),
        cubeMapArray.dimension.width,
        cubeMapArray.dimension.height,
        listOf(cubeMapArray),
        "PointLightCubeMapArrayRenderTarget",
        Vector4f(1f, 0f, 0f, 0f)
    ).apply {
        cubeMapViews.forEachIndexed { index, it ->
            textureManager.registerGeneratedCubeMap("PointLight[$index]", it)
        }
    }

    val renderTarget2D = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(
            graphicsApi.DepthBuffer(
                cubeMapArray.dimension.width,
                cubeMapArray.dimension.height,
            )
        ),
        500,
        500,
        listOf(cubemapArrayRenderTarget.cubeMapFaceViews.first()),
        "RenderTarget2D",
        Vector4f(1f, 0f, 0f, 0f)
    )

    fun bindTextures() {
        graphicsApi.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, pointLightDepthMapsArrayCube)
    }

    private val omniCamera = OmniCamera(Vector3f())
    fun renderPointLightShadowMaps(renderState: RenderState) = graphicsApi.run {
        val pointLights = renderState[pointLightStateHolder.lightState].pointLightBuffer
        val pointLightCount = renderState[pointLightStateHolder.lightState].pointLightCount
        val needToRedraw = movementSystem.anyEntityHasMoved
        if(!needToRedraw) return

        profiled("PointLight shadowmaps") {
            depthMask = true
            depthTest = true
            cullFace = true
            // TODO: Don't clear all imges always
            cubemapArrayRenderTarget.cubeMapViews.forEach {
                graphicsApi.clearTexImage(
                    it.id,
                    Format.RGBA,
                    0,
                    TexelComponentType.Float,
                    BufferUtils.createFloatBuffer(4).apply {
                        put(0f)
                        put(0f)
                        put(0f)
                        put(0f)
                        rewind()
                    }
                )
            }

            val renderWithPointCubeShadowProgram = false // TODO: Using single pass rendering doesn't work
            if(renderWithPointCubeShadowProgram) {
                cubemapArrayRenderTarget.use(true)
            } else {
                renderTarget2D.use(true)
            }

            viewPort(0, 0, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)


            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            for (lightIndex in 0 until min(MAX_POINTLIGHT_SHADOWMAPS, pointLightCount)) {

                val light = pointLights.typedBuffer.byteBuffer.run {
                    val light = pointLights.typedBuffer[lightIndex]
                    omniCamera.cameras.forEach { camera ->
                        camera.far = light.radius
                    }
                    omniCamera.updatePosition(light.position.toJoml())
                    light
                }
                if(pointLights.typedBuffer.byteBuffer.run { !light.shadow }) continue // TODO: This messes up the index, do differently

                if(renderWithPointCubeShadowProgram) {
                    pointLights.typedBuffer.byteBuffer.run {
                        pointCubeShadowPassProgram.use()
                        pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                        pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                        pointCubeShadowPassProgram.bindShaderStorageBuffer(7, entitiesState.vertexIndexBufferStatic.vertexStructArray)
                        pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", light.position.toJoml())
                        pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                        pointCubeShadowPassProgram.setUniform("lightIndex", lightIndex)

                        val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                        val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                        for (floatBufferIndex in 0..5) {
                            viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                            projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                            omniCamera.cameras[floatBufferIndex].viewMatrix.get(viewMatrices[floatBufferIndex])
                            omniCamera.cameras[floatBufferIndex].projectionMatrix.get(projectionMatrices[floatBufferIndex])

                            viewMatrices[floatBufferIndex]!!.rewind()
                            projectionMatrices[floatBufferIndex]!!.rewind()
                            pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex]!!)
                            pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex]!!)
                        }
                        pointCubeShadowPassProgram.bind()
                        profiled("PointLight shadowmap entity rendering") {
                            entitiesState.vertexIndexBufferStatic.indexBuffer.bind()
                            val renderBatchesStatic = renderState[defaultBatchesSystem.renderBatchesStatic]
                            for (batch in renderBatchesStatic.filter { it.isShadowCasting }) { // TODO: Better filtering which entity is in light radius
                                entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                                    batch.drawElementsIndirectCommand,
                                    bindIndexBuffer = false,
                                    primitiveType = PrimitiveType.Triangles,
                                    mode = RenderingMode.Fill,
                                )
                            }
                        }
                    }
                } else {
                    for (faceIndex in 0..5) {
                        graphicsApi.framebufferDepthTexture(cubemapArrayRenderTarget.cubeMapDepthFaceViews[6 * lightIndex + faceIndex], 0)
                        graphicsApi.clearDepthBuffer()
                        graphicsApi.framebufferTextureLayer(0, cubemapArrayRenderTarget.cubeMapFaceViews[6 * lightIndex + faceIndex], 0, 0)

                        renderState[staticDirectPipeline].prepare(renderState, omniCamera.cameras[faceIndex])
                        renderState[staticDirectPipeline].draw(renderState, omniCamera.cameras[faceIndex])
                    }
                }
            }
        }
        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}
