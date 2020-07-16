package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.renderer.constants.*
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.engine.model.texture.Texture
import org.lwjgl.opengl.GL11
import java.nio.IntBuffer
import java.util.concurrent.Callable
import java.util.logging.Logger
import javax.vecmath.Tuple4f

interface OpenGlExecutor {
    val openGLThreadId: Long

    suspend fun <T> execute(block: () -> T): T
    operator fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE): RETURN_TYPE

    fun shutdown()
}
interface GpuContext<T: BackendType>: OpenGlExecutor {

    val backend: T

    val window: Window<T>

    val isError: Boolean

    val availableVRAM: Int

    val availableTotalVRAM: Int

    val dedicatedVRAM: Int

    val evictedVRAM: Int

    val evictionCount: Int

    val maxTextureUnits: Int

    val fullscreenBuffer: VertexBuffer
    val debugBuffer: VertexBuffer
    val sixDebugBuffers: List<VertexBuffer>

    val registeredRenderTargets: List<RenderTarget<*>>

    val features: List<GpuFeature>

    fun createNewGPUFenceForReadState(currentReadState: RenderState)

    fun update(seconds: Float)

    var cullFace: Boolean
    var cullMode: CullMode
    var depthTest: Boolean
    var blend: Boolean

    fun enable(cap: GlCap)

    fun disable(cap: GlCap)

    fun activeTexture(textureUnitIndex: Int)

    fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int)

    fun bindTexture(target: GlTextureTarget, textureId: Int) {
        bindTexture(0, target, textureId)
    }

    fun bindTexture(textureUnitIndex: Int, texture: Texture) {
        bindTexture(textureUnitIndex, texture.target, texture.id)
    }

    fun bindTexture(texture: Texture) {
        bindTexture(texture.target, texture.id)
    }

    fun bindTextures(textureIds: IntBuffer)

    fun bindTextures(count: Int, textureIds: IntBuffer)

    fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer)

    fun unbindTexture(textureUnitIndex: Int, texture: Texture) {
        bindTexture(textureUnitIndex, texture.target, 0)
    }


    fun viewPort(x: Int, y: Int, width: Int, height: Int)

    fun clearColorBuffer()

    fun clearDepthBuffer()

    fun clearDepthAndColorBuffer()

    fun bindFrameBuffer(frameBuffer: Int)

    var depthMask: Boolean

    var depthFunc: GlDepthFunc

    fun readBuffer(colorAttachmentIndex: Int)

    var blendEquation: BlendMode

    fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor)

    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    val clearColor: Tuple4f

    fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Int, internalFormat: Int)

    fun genTextures(): Int

    fun createProgramId(): Int

    fun destroy()


    fun genFrameBuffer(): Int

    fun clearCubeMap(i: Int, textureFormat: Int)

    fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int)

    fun register(target: RenderTarget<*>)

    fun finishFrame(renderState: RenderState)

    fun isSupported(feature: GpuFeature): Boolean

    @JvmDefault
    fun isSupported(vararg features: GpuFeature) = isSupported(features.toList())

    @JvmDefault
    fun isSupported(features: List<GpuFeature>): SupportResult {
        features.filter { !isSupported(it) }.let { unsupportedFeatures ->
            return if(unsupportedFeatures.isEmpty()) {
                SupportResult.Supported
            } else SupportResult.UnSupported(unsupportedFeatures)
        }
    }

    fun getExceptionOnError(errorMessage: String = ""): RuntimeException?
    fun bindFrameBuffer(frameBuffer: FrameBuffer)
    fun checkCommandSyncs()

    sealed class SupportResult {
        object Supported: SupportResult()
        data class UnSupported(val unsupportedFeatures: List<GpuFeature>): SupportResult()
    }

    companion object {
        val CHECKERRORS = true

        val LOGGER = Logger.getLogger(GpuContext::class.java.name)

        @JvmStatic
        fun exitOnGLError(errorMessage: String) {
            if (!CHECKERRORS) {
                return
            }

            val errorValue = GL11.glGetError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR - $errorMessage: $errorString")

                RuntimeException("").printStackTrace()
                System.exit(-1)
            }
        }
    }
}
