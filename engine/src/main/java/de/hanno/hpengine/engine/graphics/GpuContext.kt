package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.renderer.constants.*
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.util.commandqueue.FutureCallable
import org.lwjgl.opengl.GL11
import java.nio.IntBuffer
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

interface GpuContext {

    val frontBuffer: RenderTarget

    val windowHandle: Long

    var canvasWidth: Int

    var canvasHeight: Int

    val isError: Boolean

    val availableVRAM: Int

    val availableTotalVRAM: Int

    val dedicatedVRAM: Int

    val evictedVRAM: Int

    val evictionCount: Int

    val isInitialized: Boolean

    val maxTextureUnits: Int

    val fullscreenBuffer: VertexBuffer
    val debugBuffer: VertexBuffer

    val registeredRenderTargets: List<RenderTarget>

    fun createNewGPUFenceForReadState(currentReadState: RenderState)

    fun registerPerFrameCommand(perFrameCommandProvider: PerFrameCommandProvider)

    fun update(seconds: Float)

    fun enable(cap: GlCap)

    fun disable(cap: GlCap)

    fun activeTexture(textureUnitIndex: Int)

    fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int)

    fun bindTexture(target: GlTextureTarget, textureId: Int) {
        bindTexture(0, target, textureId)
    }

    fun bindTexture(textureUnitIndex: Int, texture: Texture<*>) {
        bindTexture(textureUnitIndex, texture.target, texture.textureId)
    }

    fun bindTexture(texture: Texture<*>) {
        bindTexture(texture.target, texture.textureId)
    }

    fun bindTextures(textureIds: IntBuffer)

    fun bindTextures(count: Int, textureIds: IntBuffer)

    fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer)

    fun unbindTexture(textureUnitIndex: Int, texture: Texture<*>) {
        bindTexture(textureUnitIndex, texture.target, 0)
    }


    fun viewPort(x: Int, y: Int, width: Int, height: Int)

    fun clearColorBuffer()

    fun clearDepthBuffer()

    fun clearDepthAndColorBuffer()

    fun bindFrameBuffer(frameBuffer: Int)

    fun depthMask(flag: Boolean)

    fun depthFunc(func: GlDepthFunc)

    fun readBuffer(colorAttachmentIndex: Int)

    fun blendEquation(mode: BlendMode)

    fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor)

    fun cullFace(mode: CullMode)

    fun clearColor(r: Float, g: Float, b: Float, a: Float)

    fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Int, internalFormat: Int)

    fun genTextures(): Int

    fun execute(runnable: Runnable) {
        execute(runnable, true)
    }
    fun execute(runnable: () -> Unit) {
        execute(Runnable(runnable), true)
    }

    fun execute(runnable: Runnable, andBlock: Boolean)
    fun execute(runnable: () -> Unit, andBlock: Boolean) {
        return execute(Runnable(runnable), andBlock)
    }

    fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE
    fun <RETURN_TYPE> calculate(callable: () -> RETURN_TYPE): RETURN_TYPE {
        return calculate(Callable(callable))
    }

    fun <RETURN_TYPE> execute(command: FutureCallable<RETURN_TYPE>): CompletableFuture<RETURN_TYPE>

    fun blockUntilEmpty(): Long

    fun createProgramId(): Int

    fun benchmark(runnable: Runnable)

    fun destroy()


    fun genFrameBuffer(): Int

    fun clearCubeMap(i: Int, textureFormat: Int)

    fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int)

    fun register(target: RenderTarget)

    fun finishFrame(renderState: RenderState)

    fun pollEvents()

    companion object {
        val CHECKERRORS = false // TODO: Enable this as soon as possible

        val LOGGER = Logger.getLogger(GpuContext::class.java.name)

        fun create(): GpuContext? {
            val gpuContextClass = Config.getInstance().gpuContextClass
            try {
                LOGGER.info("GpuContext is being initialized")
                return gpuContextClass.newInstance()
            } catch (e: IllegalAccessException) {
                LOGGER.severe("GpuContext class " + gpuContextClass.canonicalName + " probably doesn't feature a public no args constructor")
                e.printStackTrace()
            } catch (e: InstantiationException) {
                LOGGER.severe("GpuContext class " + gpuContextClass.canonicalName + " probably doesn't feature a public no args constructor")
                e.printStackTrace()
            }

            return null
        }

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
        @JvmStatic
        fun exitOnGLError(errorMessage: () -> String) {
            if (!CHECKERRORS) {
                return
            }

            val errorValue = GL11.glGetError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                RuntimeException("").printStackTrace()
                System.exit(-1)
            }
        }
        @JvmStatic
        fun checkGLError(errorMessage: () -> String) {
            if (!CHECKERRORS) {
                return
            }

            val errorValue = GL11.glGetError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                RuntimeException("").printStackTrace()
            }
        }
    }
}
