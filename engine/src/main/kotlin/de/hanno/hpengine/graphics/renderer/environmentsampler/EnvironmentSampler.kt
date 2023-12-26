package de.hanno.hpengine.graphics.renderer.environmentsampler


import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.shader.ProgramImpl
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import de.hanno.hpengine.graphics.buffer.vertex.VertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer.invoke
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbeComponent
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import java.util.HashSet


class EnvironmentSampler(
    private val graphicsApi: GraphicsApi,
    private val transform: Transform,
    probe: EnvironmentProbeComponent,
    width: Int, height: Int, probeIndex: Int,
    programManager: ProgramManager,
    config: Config,
    textureManager: OpenGLTextureManager,
    cubeMapArrayRenderTarget: CubeMapArrayRenderTarget,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
) {
    val cubeMapProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_fragment.glsl").toCodeSource()
        )
    }
    private val cubeMapLightingProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_lighting_fragment.glsl").toCodeSource()
        )
    }
    private val depthPrePassProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/cubemap_fragment.glsl").toCodeSource()
        )
    }
    val tiledProbeLightingProgram =
        programManager.getComputeProgram(config.EngineAsset("shaders/tiled_probe_lighting_probe_rendering_compute.glsl"))
    val cubemapRadianceProgram = programManager.getComputeProgram(config.EngineAsset("shaders/cubemap_radiance_compute.glsl"))
    val cubemapRadianceFragmentProgram = config.run {
        programManager.getProgram(
            config.EngineAsset("shaders/passthrough_vertex.glsl").toCodeSource(),
            config.EngineAsset("shaders/cubemap_radiance_fragment.glsl").toCodeSource()
        )
    }
    private val entityBuffer = BufferUtils.createFloatBuffer(16)

    var drawnOnce = false

    var sidesDrawn: MutableSet<Int> = HashSet()

    val probe: EnvironmentProbeComponent
    val fullscreenBuffer: VertexBuffer
    val cubeMapView: Int
    val cubeMapView1: Int
    val cubeMapView2: Int
    val cubeMapFaceViews = Array(4) { IntArray(6) }
    private val secondPassPointProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/second_pass_point_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/scond_pass_point_fragment.glsl").toCodeSource()
        )
    }
    private val secondPassTubeProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/second_pass_point_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/scond_pass_tube_fragment.glsl").toCodeSource()
        )
    }
    private val secondPassAreaProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/second_pass_area_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/scond_pass_area_fragment.glsl").toCodeSource()
        )
    }
    val secondPassDirectionalProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/second_pass_directional_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/scond_pass_directional_fragment.glsl").toCodeSource()
        )
    }
    val firstPassDefaultProgram = config.run {
        programManager.getProgram(
            EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/frst_pass_fragment.glsl").toCodeSource()
        )
    }
    val renderTarget: RenderTarget2D
    val camera: Camera

    var gpuContext = graphicsApi
    val config: Config
    val textureManager: OpenGLTextureManager

    fun registerSideAsDrawn(i: Int) {
        sidesDrawn.add(i)
        if (sidesDrawn.size == 6) {
            drawnOnce = true
        }
    }

    fun resetDrawing() {
        sidesDrawn.clear()
        drawnOnce = false
    }

    fun drawEntities(
        renderState: RenderState, program: ProgramImpl<Uniforms>,
        viewMatrixAsBuffer: FloatBuffer, projectionMatrixAsBuffer: FloatBuffer,
        viewProjectionMatrixAsBuffer: FloatBuffer
    ) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        graphicsApi.run {
            program.use()
            bindShaderSpecificsPerCubeMapSide(
                renderState,
                viewMatrixAsBuffer,
                projectionMatrixAsBuffer,
                viewProjectionMatrixAsBuffer,
                program
            )
            for (e in renderState[defaultBatchesSystem.renderBatchesStatic]) {
//                if (!isInFrustum(camera.frustum, e.centerWorld, e.entityMinWorld, e.entityMaxWorld)) {
//				continue;
//                }
                entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                    e.drawElementsIndirectCommand,
                    true,
                    PrimitiveType.Triangles,
                    RenderingMode.Fill
                )
            }
        }
    }

    private fun bindShaderSpecificsPerCubeMapSide(
        renderState: RenderState,
        viewMatrixAsBuffer: FloatBuffer,
        projectionMatrixAsBuffer: FloatBuffer,
        viewProjectionMatrixAsBuffer: FloatBuffer,
        program: ProgramImpl<Uniforms>
    ) {
        val light = renderState[directionalLightStateHolder.lightState]
//        TODO: Reimplement
//        program.setUniform("lightDirection", light!!.entity.transform.viewDirection)
//        program.setUniform("lightDiffuse", light.color)
//        program.setUniform("lightAmbient", config.effects.ambientLight)
//        program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
//        program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
//        program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)
//        program.setUniformAsMatrix4("shadowMatrix", light.viewProjectionMatrixAsBuffer)
    }


    fun rotateForIndex(i: Int, transform: Transform, camera: CameraComponent) {
        val deltaNear = 0.0f
        val deltaFar = 100.0f
        val halfSizeX = probe.size.x / 2
        val halfSizeY = probe.size.y / 2
        val halfSizeZ = probe.size.z / 2
        val position = transform.position
        when (i) {
            0 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                camera.far = halfSizeX * deltaFar
            }
            1 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                camera.far = halfSizeX * deltaFar
            }
            2 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(90.0).toFloat()))
                transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                camera.far = halfSizeY * deltaFar
            }
            3 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                camera.far = halfSizeY * deltaFar
            }
            4 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                camera.far = halfSizeZ * deltaFar
            }
            5 -> {
                transform.rotation(Quaternionf().identity())
                transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                //			de.hanno.hpengine.camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                camera.far = halfSizeZ * deltaFar
            }
            else -> {
            }
        }
    }

    init {
        this.config = config
        this.textureManager = textureManager
        val camera = Camera(transform, near = 0.1f, far = 5000f, fov = 90f, ratio = 1f)
        camera.width = width.toFloat()
        camera.width = height.toFloat()
        this.camera = camera
        this.probe = probe
        val cubeMapCamInitialOrientation = Quaternionf().identity()
        transform.rotate(cubeMapCamInitialOrientation)

        cubeMapView = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(0), probeIndex).id
        cubeMapView1 = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(1), probeIndex).id
        cubeMapView2 = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(2), probeIndex).id

        val diffuseInternalFormat = cubeMapArrayRenderTarget.getCubeMapArray(3).internalFormat
        for (faceIndex in 0..5) {
            cubeMapFaceViews[0][faceIndex] = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(0), probeIndex, faceIndex).id
            cubeMapFaceViews[1][faceIndex] = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(1), probeIndex, faceIndex).id
            cubeMapFaceViews[2][faceIndex] = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(2), probeIndex, faceIndex).id
            cubeMapFaceViews[3][faceIndex] = graphicsApi.createView(cubeMapArrayRenderTarget.getCubeMapArray(3), probeIndex, faceIndex).id
        }
        renderTarget = graphicsApi.RenderTarget(
            graphicsApi.FrameBuffer(graphicsApi.DepthBuffer(RESOLUTION, RESOLUTION)),
            RESOLUTION, RESOLUTION,
            listOf(ColorAttachmentDefinition("Environment Diffuse", diffuseInternalFormat)).toTextures(
                graphicsApi,
                RESOLUTION,
                RESOLUTION
            ),
            "Environment Sampler",
            Vector4f(),
        )
        fullscreenBuffer = QuadVertexBuffer(graphicsApi)
        fullscreenBuffer.upload()
    }
}

private val RESOLUTION = 512
private val CUBEMAP_MIPMAP_COUNT = calculateMipMapCount(RESOLUTION)
