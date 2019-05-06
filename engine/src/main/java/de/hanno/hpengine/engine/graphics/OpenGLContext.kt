package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.QuadVertexBuffer
import de.hanno.hpengine.util.commandqueue.FutureCallable
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_STICKY_KEYS
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwSetErrorCallback
import org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback
import org.lwjgl.glfw.GLFW.glfwSetInputMode
import org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback
import org.lwjgl.glfw.GLFW.glfwShowWindow
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.glfw.GLFW.glfwSwapInterval
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL11.GL_VERSION
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.NVXGPUMemoryInfo
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

class OpenGLContext private constructor() : GpuContext<OpenGl> {
    override val backend = object: OpenGl {
        override val gpuContext = this@OpenGLContext
    }

    override lateinit var frontBuffer: RenderTarget
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    override val registeredRenderTargets = ArrayList<RenderTarget>()


    override var canvasWidth = Config.getInstance().width
    override var canvasHeight = Config.getInstance().height

    internal val channel = Channel<FutureCallable<*>>(Channel.UNLIMITED)

    @Volatile
    override var isInitialized = false
        private set
    override var maxTextureUnits: Int = 0
    private val perFrameCommandProviders = CopyOnWriteArrayList<PerFrameCommandProvider>()
    //     Don't make this a local field, we need a string reference
    private val errorCallback: GLFWErrorCallbackI = GLFWErrorCallbackI { error: Int, description: Long -> GLFWErrorCallback.createPrint(System.err) }
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


    override lateinit var fullscreenBuffer: QuadVertexBuffer
    override lateinit var debugBuffer: QuadVertexBuffer

    private lateinit var extensions: String

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
        Executor.launch {
            try {
                privateInit()
            } catch (e: Exception) {
                LOGGER.severe("Exception during privateInit")
                e.printStackTrace()
                System.exit(-1)
            }
            yield()
        }
        waitForInitialization()
        startEndlessLoop()
        fullscreenBuffer = QuadVertexBuffer(this, true)
        debugBuffer = QuadVertexBuffer(this, false)
        fullscreenBuffer.upload()
        debugBuffer.upload()
    }

    override val features = run {
        val bindlessTextures = if(extensions.contains("ARB_bindless_textures")) BindlessTextures else null
        val drawParameters = if(extensions.contains("GL_ARB_shader_draw_parameters")) DrawParameters else null
        val shader5 = if(extensions.contentEquals("GL_NV_gpu_shader5")) Shader5 else null

        listOfNotNull(bindlessTextures, drawParameters, shader5)
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

    internal fun privateInit() {
        Executor.openGLThreadId = Thread.currentThread().id

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
                } catch (e: Exception) { e.printStackTrace() }

            }
        }
        glfwSetFramebufferSizeCallback(windowHandle, framebufferSizeCallback)
        glfwSetWindowCloseCallback(windowHandle, closeCallback)

        glfwSetInputMode(windowHandle, GLFW_STICKY_KEYS, 1)
        glfwSwapInterval(1)
        GL.createCapabilities()
//        glfwWindowHint(GLFW_VISIBLE, GL_FALSE)
        glfwShowWindow(windowHandle)

        println("OpenGL version: " + GL11.glGetString(GL_VERSION))
        val numExtensions = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS)
        val supportedExtensions = (0 until numExtensions).map { GL30.glGetStringi(GL11.GL_EXTENSIONS, it) }

        extensions = supportedExtensions.joinToString(" ")

        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
//        GL43.glDebugMessageCallback(new KHRDebugCallback(handler));

        enable(GlCap.DEPTH_TEST)
        enable(GlCap.CULL_FACE)

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.getInstance().width, Config.getInstance().height)
        maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)

        frontBuffer = createFrontBuffer()

        isInitialized = true
//        LOGGER.info("OpenGLContext isInitialized")
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

    internal fun checkCommandSyncs() {
        commandSyncs = checkCommandSyncsReturnUnsignaled(commandSyncs)
    }

    internal fun executePerFrameCommands() {
        for (i in perFrameCommandProviders.indices) {
            val provider = perFrameCommandProviders[i]
            executePerFrameCommand(provider)
        }
    }

    internal fun executePerFrameCommand(perFrameCommandProvider: PerFrameCommandProvider) {
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
        when {
            isOpenGLThread -> {
                runnable.run()
                executeExitOnGlErrorFunction { "Error in runnable" }
            }
            andBlock -> Executor.future {
                runnable.run()
//                executeExitOnGlErrorFunction { "Error in runnable" }()
            }.join()
            else -> Executor.launch {
                runnable.run()
//                executeExitOnGlErrorFunction { "Error in runnable" }()
            }
        }
    }

    fun executeExitOnGlErrorFunction(errorMessage: () -> String) {
        if (GpuContext.CHECKERRORS) {
            val errorValue = GL11.glGetError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                RuntimeException("").printStackTrace()
                System.exit(-1)
            }
        }
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) {
            return callable.call().apply {
                exitOnGLError { "Error in command" }
            }
        }
        return runBlocking {
            withContext(Executor.coroutineContext) {
                callable.call()
            }
        }
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
            Executor.openGlExecutor.shutdown()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }


    private fun startEndlessLoop() {
        Executor.launch {
            while (true) {
                pollEvents()
                exitOnGLError("")
                checkCommandSyncs()
                exitOnGLError("")
                executePerFrameCommands()
                exitOnGLError("")
                var callable: FutureCallable<*>? = channel.poll()
                while (callable != null) {
                    val result = callable.execute()
                    exitOnGLError("")
                    (callable.future as CompletableFuture<Any>).complete(result)
                    callable = channel.poll()
                }
                yield()
                exitOnGLError("")
            }
        }
        println("OpenGLContext thread submitted with id ${Executor.openGLThreadId}")
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

    fun getOpenGlExtensionsDefine(): String {

        fun String.appendIfSupported(feature: GpuFeature, string: String): String {
            val supported = isSupported(feature)
            val defineString = if (supported) "#define ${feature.toString().toUpperCase()} true\n" else ""
            val featureStringOrEmpty = if (supported) " $string\n" else ""
            return "${this}$featureStringOrEmpty$defineString"
        }
        return "".appendIfSupported(Shader5, "#extension GL_NV_gpu_shader5 : enable")
                .appendIfSupported(BindlessTextures, "#extension GL_ARB_bindless_texture : enable")
                .appendIfSupported(DrawParameters, "#extension GL_ARB_shader_draw_parameters : enable")
    }

    override fun isSupported(feature: GpuFeature) = features.contains(feature)

    fun getOpenGlVersionsDefine(): String = "#version 430 core\n"


    fun exitOnGLError(errorMessage: String) {
        exitOnGLError { errorMessage }
    }
    fun exitOnGLError(errorMessage: () -> String) {
        execute {
            executeExitOnGlErrorFunction(errorMessage)
        }
    }

    fun checkGLError(errorMessage: () -> String) = execute {
        if (GpuContext.CHECKERRORS) {

            val errorValue = GL11.glGetError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                RuntimeException("").printStackTrace()
            }
        }
    }

    object Executor: CoroutineScope {
        internal var openGLThreadId: Long = -1

        internal val openGlExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable).apply {
                name = OpenGLContext.OPENGL_THREAD_NAME
            }
        }

        val dispatcher = Executor.openGlExecutor.asCoroutineDispatcher()

//        private val job = Job()
//        override val coroutineContext = dispatcher + job
        override val coroutineContext
            get() = dispatcher + Job()

        fun launch(start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> Unit): Job {
            val enhancedBlock: suspend CoroutineScope.() -> Unit = { block(); executeExitOnGlErrorFunction(); }
            return (CoroutineScope::launch)(this, coroutineContext, start, enhancedBlock)
        }

        // duplicate code, because I had compilation issues with coroutines and this function somehow
        private fun executeExitOnGlErrorFunction(errorMessage: () -> String = {""}) {
            if (GpuContext.CHECKERRORS) {
                val errorValue = GL11.glGetError()

                if (errorValue != GL11.GL_NO_ERROR) {
                    val errorString = GLU.gluErrorString(errorValue)
                    System.err.println("ERROR: $errorString")
                    System.err.println(errorMessage())

                    RuntimeException("").printStackTrace()
                    System.exit(-1)
                }
            }
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(OpenGLContext::class.java.name)

        const val OPENGL_THREAD_NAME = "OpenGLContext"

        val ZERO_BUFFER: FloatBuffer = BufferUtils.createFloatBuffer(4).apply {
            put(0f)
            put(0f)
            put(0f)
            put(0f)
            rewind()
        }

        inline val dispatcher
            get() = Executor.dispatcher

        private val openGLContextSingleton = OpenGLContext()

        @JvmStatic @JvmName("get") operator fun invoke(): OpenGLContext {
            return openGLContextSingleton
        }


        fun getExitOnGlErrorFunction(errorMessage: () -> String = { "" }): () -> Unit = {
            if (GpuContext.CHECKERRORS) {
                val errorValue = GL11.glGetError()

                if (errorValue != GL11.GL_NO_ERROR) {
                    val errorString = GLU.gluErrorString(errorValue)
                    System.err.println("ERROR: $errorString")
                    System.err.println(errorMessage())

                    RuntimeException("").printStackTrace()
                    System.exit(-1)
                }
            }
        }

    }
}

inline val isOpenGLThread: Boolean
    get() {
        return Thread.currentThread().isOpenGLThread
    }

val Thread.isOpenGLThread: Boolean
    get() {
        return id == OpenGLContext.Executor.openGLThreadId
    }