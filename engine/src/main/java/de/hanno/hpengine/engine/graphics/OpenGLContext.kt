package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.constants.*
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.QuadVertexBuffer
import de.hanno.hpengine.util.commandqueue.FutureCallable
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.opengl.*
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL30.glGenFramebuffers
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

class OpenGLContext : GpuContext {
    override lateinit var frontBuffer: RenderTarget
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    override val registeredRenderTargets = ArrayList<RenderTarget>()


    override var canvasWidth = Config.getInstance().width
    override var canvasHeight = Config.getInstance().height

    private val openGlExecutor = Executors.newSingleThreadExecutor()
    private val dispatcher = openGlExecutor.asCoroutineDispatcher()
    private val channel = Channel<FutureCallable<*>>()

    @Volatile
    override var isInitialized = false
        private set
    override var maxTextureUnits: Int = 0
    private val perFrameCommandProviders = CopyOnWriteArrayList<PerFrameCommandProvider>()
    //     Don't make this a local field, we need a string reference
    private var errorCallback: GLFWErrorCallbackI = GLFWErrorCallbackI { error: Int, description: Long -> GLFWErrorCallback.createPrint(System.err) }
    override var windowHandle: Long = 0
    // Don't remove these strong references
    private var framebufferSizeCallback: GLFWFramebufferSizeCallback? = null
    private val closeCallback = { l: Long -> System.exit(0) }

    private val activeTexture = -1

    private val textureBindings = HashMap<Int, Int>()

    private var currentFrameBuffer = -1

    private var depthMask = false

    internal var currentReadBuffer = -1

    private val currentBlendMode = BlendMode.FUNC_ADD


    override val fullscreenBuffer: QuadVertexBuffer
    override val debugBuffer: QuadVertexBuffer

    override val isError: Boolean
        get() {
            return calculate(Callable{ GL11.glGetError() != GL11.GL_NO_ERROR })!!
        }


    fun createFrontBuffer(): RenderTarget {
        val frontBuffer = object : RenderTarget(this) {
            override fun getWidth(): Int {
                return canvasWidth
            }

            override fun getHeight(): Int {
                return canvasHeight
            }

            override fun use(clear: Boolean) {
                super.use(false)
            }
        }
        frontBuffer.frameBuffer = 0
        return frontBuffer
    }

    init {
        init()
        fullscreenBuffer = QuadVertexBuffer(this, true)
        debugBuffer = QuadVertexBuffer(this, false)

        fullscreenBuffer.upload()
        debugBuffer.upload()
    }

    override fun createNewGPUFenceForReadState(currentReadState: RenderState) {
        currentReadState.gpuCommandSync = createCommandSync()
    }

    private fun createCommandSync(): OpenGlCommandSync {
        val openGlCommandSync = OpenGlCommandSync()
        commandSyncs.add(openGlCommandSync)
        return openGlCommandSync
    }

    override fun registerPerFrameCommand(perFrameCommandProvider: PerFrameCommandProvider) {
        this.perFrameCommandProviders.add(perFrameCommandProvider)
    }

    private fun privateInit() {
        glfwSetErrorCallback(errorCallback)
        glfwInit()
        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        windowHandle = glfwCreateWindow(Config.getInstance().width, Config.getInstance().height, "HPEngine", 0, 0)
        if (windowHandle == 0L) {
            throw RuntimeException("Failed to create windowHandle")
        }

        glfwMakeContextCurrent(windowHandle)
        framebufferSizeCallback = object : GLFWFramebufferSizeCallback() {
            override fun invoke(window: Long, width: Int, height: Int) {
                try {
                    this@OpenGLContext.canvasWidth = width
                    this@OpenGLContext.canvasHeight = height
                } catch (e: Exception) {
                }

            }
        }
        glfwSetFramebufferSizeCallback(windowHandle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(windowHandle, closeCallback)

        glfwSetInputMode(windowHandle, GLFW_STICKY_KEYS, 1)
        glfwSwapInterval(1)
        GL.createCapabilities()
        glfwShowWindow(windowHandle)

        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        //        GL43.glDebugMessageCallback(new KHRDebugCallback(handler));

        enable(GlCap.DEPTH_TEST)
        enable(GlCap.CULL_FACE)

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.getInstance().width, Config.getInstance().height)
        maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)

        frontBuffer = createFrontBuffer()

        isInitialized = true
        LOGGER.info("OpenGLContext isInitialized")
    }

    override fun update(seconds: Float) {
        pollEvents()
        checkCommandSyncs()
        try {
            executePerFrameCommands()
        } catch (e: Error) {
            LOGGER.log(Level.SEVERE, "", e)
        }

    }

    private fun checkCommandSyncs() {
        commandSyncs = checkCommandSyncsReturnUnsignaled(commandSyncs)
    }

    private fun executePerFrameCommands() {
        for (i in perFrameCommandProviders.indices) {
            val provider = perFrameCommandProviders[i]
            executePerFrameCommand(provider)
        }
    }

    private fun executePerFrameCommand(perFrameCommandProvider: PerFrameCommandProvider) {
        if (perFrameCommandProvider.isReadyForExecution()) {
            perFrameCommandProvider.execute()
        }
    }

    override fun enable(cap: GlCap) {
        cap.enable()
    }

    override fun disable(cap: GlCap) {
        cap.disable()
    }

    override fun activeTexture(textureUnitIndex: Int) {
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
        //        TODO: Use this
        //        if(activeTexture != textureIndexGLInt)
        execute(Runnable { GL13.glActiveTexture(textureIndexGLInt) })
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }

    override fun bindTexture(target: GlTextureTarget, textureId: Int) {
        execute(Runnable {
            GL11.glBindTexture(target.glTarget, textureId)
            textureBindings[getCleanedTextureUnitValue(activeTexture)] = textureId
        })
    }

    private fun printTextureBindings() {
        for ((key, value) in textureBindings) {
            LOGGER.info("Slot $key -> Texture $value")
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int) {
        execute(Runnable {
            activeTexture(textureUnitIndex)
            GL11.glBindTexture(target.glTarget, textureId)
            textureBindings[textureUnitIndex] = textureId
        })
    }

    override fun bindTextures(textureIds: IntBuffer) {
        execute(Runnable { GL44.glBindTextures(0, textureIds) })
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        execute(Runnable { GL44.glBindTextures(0, textureIds) })
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        execute(Runnable { GL44.glBindTextures(firstUnit, textureIds) })
    }

    override fun viewPort(x: Int, y: Int, width: Int, height: Int) {
        GL11.glViewport(x, y, width, height)
    }

    override fun clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
    }

    override fun clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
    }

    override fun clearDepthAndColorBuffer() {
        clearDepthBuffer()
        clearColorBuffer()
    }

    override fun bindFrameBuffer(frameBuffer: Int) {
        //        if(currentFrameBuffer != frameBuffer) {
        execute(Runnable{
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
            currentFrameBuffer = frameBuffer
        })
        //        }
    }

    override fun depthMask(flag: Boolean) {
        if (depthMask != flag) {
            GL11.glDepthMask(flag)
            depthMask = flag
        }
    }

    override fun depthFunc(func: GlDepthFunc) {
        GL11.glDepthFunc(func.glFunc)
    }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        if (currentReadBuffer != colorAttachment) {
            GL11.glReadBuffer(colorAttachment)
        }
    }

    override fun blendEquation(mode: BlendMode) {
        GL14.glBlendEquation(mode.mode)
    }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) {
        GL11.glBlendFunc(sfactor.glFactor, dfactor.glFactor)
    }

    override fun cullFace(mode: CullMode) {
        GL11.glCullFace(mode.glMode)
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        calculate(Callable { GL11.glClearColor(r, g, b, a) })
    }

    override fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Int, internalFormat: Int) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat)
    }

    override fun genTextures(): Int {
        return calculate(Callable { GL11.glGenTextures() })!!
    }

    override val availableVRAM: Int
        get() = calculate(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) })!!

    override val availableTotalVRAM: Int
        get() = calculate(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) })!!

    override val dedicatedVRAM: Int
        get() = calculate(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) })!!

    override val evictedVRAM: Int
        get() = calculate(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX) })!!

    override val evictionCount: Int
        get() = calculate(Callable{ GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) })!!

    override fun execute(runnable: Runnable, andBlock: Boolean) {
        if(isOpenGLThread) {
            runnable.run()
        } else if(andBlock) {
            runBlocking {
                launch(dispatcher) {
                    runnable.run()
                }
            }
        } else {
            GlobalScope.launch {
                runnable.run()
            }
        }
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) {
            return callable.call()
        }
        return runBlocking {
            withContext(dispatcher) {
                callable.call()
            }
        }
    }

    override fun blockUntilEmpty(): Long {
        val start = System.currentTimeMillis()
        while(!channel.isEmpty) {
            try {
                Thread.sleep(0, 100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        return System.currentTimeMillis() - start
    }

    override fun createProgramId(): Int {
        return calculate(Callable{ GL20.glCreateProgram() })!!
    }

    override fun benchmark(runnable: Runnable) {
        //        GLTimerQuery.getInstance().begin();
        //        runnable.run();
        //        GLTimerQuery.getInstance().end();
        //        LOGGER.info(GLTimerQuery.getInstance().getResult().toString());
    }


    override fun destroy() {
        try {
            openGlExecutor.shutdown()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }


    private fun init() {
        runBlocking(dispatcher) {
            Thread.currentThread().name = OpenGLContext.OPENGL_THREAD_NAME
            OpenGLContext.OPENGL_THREAD_ID = Thread.currentThread().id
            try {
                try {
                    this@OpenGLContext.privateInit()
                } catch (e: Exception) {
                    OpenGLContext.LOGGER.severe("Exception during privateInit")
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                System.exit(-1)
            }
        }
        GlobalScope.launch(dispatcher) {
            while(true) {
                pollEvents()
                checkCommandSyncs()
                try {
                    executePerFrameCommands()
                    var callable: FutureCallable<*>? = channel.poll()
                    while(callable != null) {
                        val result = callable.execute()
                        (callable.future as CompletableFuture<Any>).complete(result)
                        callable = channel.poll()
                    }
                    yield()
                } catch (e: Error) {
                    LOGGER.log(Level.SEVERE, "", e)
                }
            }
        }
        println("OpenGLContext thread submitted with id " + OpenGLContext.OPENGL_THREAD_ID)
        waitForInitialization()
    }

    private fun waitForInitialization() {
        OpenGLContext.LOGGER.info("Waiting for OpenGLContext initialization")
        while (!isInitialized) {
            OpenGLContext.LOGGER.info("Waiting for OpenGLContext initialization...")
            try {
                Thread.sleep(400)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
        OpenGLContext.LOGGER.info("OpenGLContext ready")
    }

    override fun genFrameBuffer(): Int {
        return calculate(Callable{ glGenFramebuffers() })!!
    }

    override fun clearCubeMap(textureId: Int, textureFormat: Int) {
        glClearTexImage(textureId, 0, textureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int) {
        glClearTexSubImage(textureID, 0, 0, 0, 6 * cubeMapIndex, width, height, 6, internalFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun register(target: RenderTarget) {
        registeredRenderTargets.add(target)
    }

    override fun finishFrame(renderState: RenderState) {
        GPUProfiler.start("Create new fence")
        createNewGPUFenceForReadState(renderState)
        GPUProfiler.end()
        //        GPUProfiler.start("Waiting for driver");
        //        TODO: Maybe move this out of the condition of buffer swapping to rendermanager?
        //        pollEvents();
        GPUProfiler.start("Swap buffers")
        glfwSwapBuffers(windowHandle)
        GPUProfiler.end()
        //        GPUProfiler.end();
    }

    override fun pollEvents() {
        GPUProfiler.start("Poll events")
        glfwPollEvents()
        GPUProfiler.end()
    }

    companion object {
        private val LOGGER = Logger.getLogger(OpenGLContext::class.java.name)

        private var OPENGL_THREAD_ID: Long = -1

        var OPENGL_THREAD_NAME = "OpenGLContext"

        var ZERO_BUFFER = BufferUtils.createFloatBuffer(4)

        init {
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.rewind()
        }

        internal val isOpenGLThread: Boolean
            get() {
                if (OpenGLContext.OPENGL_THREAD_ID == -1L) throw IllegalStateException("OpenGLThread id is -1, initialization failed!")
                return Thread.currentThread().id == OpenGLContext.OPENGL_THREAD_ID
            }
    }
}
