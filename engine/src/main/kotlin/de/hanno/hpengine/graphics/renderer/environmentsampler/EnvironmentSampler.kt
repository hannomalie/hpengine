package de.hanno.hpengine.graphics.renderer.environmentsampler


import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.artemis.CameraComponent
import de.hanno.hpengine.artemis.EnvironmentProbeComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer.Companion.invoke
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.transform.Spatial.Companion.isInFrustum
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.exitOnGLError
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.vertexbuffer.IVertexBuffer
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer.invoke
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL43
import java.nio.FloatBuffer
import java.util.HashSet


context(GpuContext)
class EnvironmentSampler(
    val transform: Transform,
    probe: EnvironmentProbeComponent,
    width: Int, height: Int, probeIndex: Int,
    programManager: ProgramManager,
    config: Config,
    textureManager: OpenGLTextureManager,
    cubeMapArrayRenderTarget: CubeMapArrayRenderTarget
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
    val fullscreenBuffer: IVertexBuffer
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
    val renderTarget: BackBufferRenderTarget<OpenGLTexture2D>
    val camera: Camera

    var gpuContext: GpuContext
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
        renderState: RenderState, program: Program<Uniforms>,
        viewMatrixAsBuffer: FloatBuffer, projectionMatrixAsBuffer: FloatBuffer,
        viewProjectionMatrixAsBuffer: FloatBuffer
    ) {
        program.use()
        bindShaderSpecificsPerCubeMapSide(
            renderState,
            viewMatrixAsBuffer,
            projectionMatrixAsBuffer,
            viewProjectionMatrixAsBuffer,
            program
        )
        for (e in renderState.renderBatchesStatic) {
            if (!isInFrustum(camera, e.centerWorld, e.entityMinWorld, e.entityMaxWorld)) {
//				continue;
            }
            renderState.vertexIndexBufferStatic.indexBuffer.draw(
                e.drawElementsIndirectCommand,
                true,
                PrimitiveType.Triangles,
                RenderingMode.Faces
            )
        }
    }

    private fun bindShaderSpecificsPerCubeMapSide(
        renderState: RenderState,
        viewMatrixAsBuffer: FloatBuffer,
        projectionMatrixAsBuffer: FloatBuffer,
        viewProjectionMatrixAsBuffer: FloatBuffer,
        program: Program<Uniforms>
    ) {
        val light = renderState.directionalLightState
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
        gpuContext = programManager.gpuContext
        this.config = config
        this.textureManager = textureManager
        val camera = Camera(transform, near = 0.1f, far = 5000f, fov = 90f, ratio = 1f)
        camera.width = width.toFloat()
        camera.width = height.toFloat()
        this.camera = camera
        this.probe = probe
        val cubeMapCamInitialOrientation = Quaternionf().identity()
        transform.rotate(cubeMapCamInitialOrientation)

        val cubeMapArrayRenderTarget = cubeMapArrayRenderTarget
        cubeMapView = GL11.glGenTextures()
        cubeMapView1 = GL11.glGenTextures()
        cubeMapView2 = GL11.glGenTextures()
        exitOnGLError("EnvironmentSampler before view creation")
        val diffuseInternalFormat = cubeMapArrayRenderTarget.getCubeMapArray(3).internalFormat
        for (z in 0..5) {
            cubeMapFaceViews[0][z] = GL11.glGenTextures()
            cubeMapFaceViews[1][z] = GL11.glGenTextures()
            cubeMapFaceViews[2][z] = GL11.glGenTextures()
            cubeMapFaceViews[3][z] = GL11.glGenTextures()
            //GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureId(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
            GL43.glTextureView(
                cubeMapFaceViews[0][z],
                GL11.GL_TEXTURE_2D,
                cubeMapArrayRenderTarget.getCubeMapArray(0).id,
                cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat,
                0,
                1,
                6 * probeIndex + z,
                1
            )
            GL43.glTextureView(
                cubeMapFaceViews[1][z],
                GL11.GL_TEXTURE_2D,
                cubeMapArrayRenderTarget.getCubeMapArray(1).id,
                cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat,
                0,
                1,
                6 * probeIndex + z,
                1
            )
            GL43.glTextureView(
                cubeMapFaceViews[2][z],
                GL11.GL_TEXTURE_2D,
                cubeMapArrayRenderTarget.getCubeMapArray(2).id,
                cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat,
                0,
                1,
                6 * probeIndex + z,
                1
            )
            GL43.glTextureView(
                cubeMapFaceViews[3][z],
                GL11.GL_TEXTURE_2D,
                cubeMapArrayRenderTarget.getCubeMapArray(3).id,
                diffuseInternalFormat,
                0,
                1,
                6 * probeIndex + z,
                1
            )
        }
        GL43.glTextureView(
            cubeMapView,
            GL13.GL_TEXTURE_CUBE_MAP,
            cubeMapArrayRenderTarget.getCubeMapArray(0).id,
            cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat,
            0,
            CUBEMAP_MIPMAP_COUNT,
            6 * probeIndex,
            6
        )
        GL43.glTextureView(
            cubeMapView1,
            GL13.GL_TEXTURE_CUBE_MAP,
            cubeMapArrayRenderTarget.getCubeMapArray(1).id,
            cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat,
            0,
            CUBEMAP_MIPMAP_COUNT,
            6 * probeIndex,
            6
        )
        GL43.glTextureView(
            cubeMapView2,
            GL13.GL_TEXTURE_CUBE_MAP,
            cubeMapArrayRenderTarget.getCubeMapArray(2).id,
            cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat,
            0,
            CUBEMAP_MIPMAP_COUNT,
            6 * probeIndex,
            6
        )
        renderTarget = RenderTarget(
            FrameBuffer(DepthBuffer(RESOLUTION, RESOLUTION)),
            RESOLUTION, RESOLUTION,
            listOf(ColorAttachmentDefinition("Environment Diffuse", diffuseInternalFormat)).toTextures(
                RESOLUTION,
                RESOLUTION
            ),
            "Environment Sampler"
        )
        fullscreenBuffer = QuadVertexBuffer()
        fullscreenBuffer.upload()
        exitOnGLError("EnvironmentSampler constructor")
    }
}

private val RESOLUTION = 512
private val CUBEMAP_MIPMAP_COUNT = Util.calculateMipMapCount(RESOLUTION)
