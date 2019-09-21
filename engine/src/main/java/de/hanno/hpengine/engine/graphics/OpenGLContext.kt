package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWErrorCallbackI
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_VERSION
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GLUtil
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
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

class OpenGLContext private constructor(val width: Int,
                                        val height: Int,
                                        override val window: GlfwWindow) : GpuContext<OpenGl> {
    override val backend = object: OpenGl {
        override val gpuContext = this@OpenGLContext
    }

    override lateinit var frontBuffer: RenderTarget
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    override val registeredRenderTargets = ArrayList<RenderTarget>()

    internal val channel = Channel<FutureCallable<*>>(Channel.UNLIMITED)

    override var isInitialized = false
        private set
    override var maxTextureUnits: Int = 0
    private val perFrameCommandProviders = CopyOnWriteArrayList<PerFrameCommandProvider>()

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
                return window.width
            }

            override fun getHeight(): Int {
                return window.height
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
                exitProcess(-1)
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

        glfwMakeContextCurrent(window.handle)

        GL.createCapabilities()

        println("OpenGL version: " + GL11.glGetString(GL_VERSION))
        val numExtensions = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS)
        val supportedExtensions = (0 until numExtensions).map { GL30.glGetStringi(GL11.GL_EXTENSIONS, it) }

        extensions = supportedExtensions.joinToString(" ")

        val debug = true
        if(debug) {
            val debugProc = GLUtil.setupDebugMessageCallback()
        }

        enable(GlCap.DEPTH_TEST)
        enable(GlCap.CULL_FACE)

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, window.width, window.height)
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
    internal fun executeAfterFrameActions() {
        for (i in perFrameCommandProviders.indices) {
            perFrameCommandProviders[i].postFrame()
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
        if(textureUnitIndex < 0) { throw IllegalArgumentException("Passed textureUnitIndex of < 0") }
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
//        TODO: Use this
//        if(activeTexture != textureIndexGLInt)
        execute("activeTexture", Runnable { GL13.glActiveTexture(textureIndexGLInt) })
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }
    override fun bindTexture(target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        execute("bindTexture0", Runnable {
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("")?.let{ throw it }
        })
    }

    override fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        execute("bindTexture1", Runnable {
            getExceptionOnError("beforeBindTexture")?.let{ throw it }
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("bindTexture")?.let{ throw it }
        })
    }

    override fun bindTextures(textureIds: IntBuffer) {
        execute("bindTextures0", Runnable { GL44.glBindTextures(0, textureIds) })
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        execute("bindTextures1", Runnable { GL44.glBindTextures(0, textureIds) })
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        execute("bindTextures2", Runnable { GL44.glBindTextures(firstUnit, textureIds) })
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
        execute("bindFrameBuffer", Runnable{
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
        })
    }

    override fun depthMask(flag: Boolean) {
        GL11.glDepthMask(flag)
    }

    override fun depthFunc(func: GlDepthFunc) {
        GL11.glDepthFunc(func.glFunc)
    }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        GL11.glReadBuffer(colorAttachment)
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

    override fun execute(actionName: String, runnable: java.lang.Runnable, andBlock: Boolean) {
        val runtimeException: RuntimeException? = when {
            isOpenGLThread -> {
                runnable.run()
                getExceptionOnError { "Error in action $actionName in $runnable" }
            }
            andBlock -> Executor.future {
                runnable.run()
                getExceptionOnError { "Error in action $actionName in $runnable" }
            }.get()
            else -> Executor.async {
                runnable.run()
                getExceptionOnError { "Error in action $actionName in $runnable" }
            }.getCompleted()
        }
        runtimeException?.printStackTrace()
    }

    fun getExceptionOnError(errorMessage: () -> String = { "" }): RuntimeException? {
        if (GpuContext.CHECKERRORS) {
            val errorValue = getError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                return RuntimeException("$errorString\n$errorMessage")
            }
        }
        return null
    }

    override fun <RETURN_TYPE> calculate(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) {
            return callable.call()
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
                profiled("Frame") {
                    pollEvents()
                    getExceptionOnError("")
                    checkCommandSyncs()
                    getExceptionOnError("")
                    executePerFrameCommands()
                    getExceptionOnError("")
                    var callable: FutureCallable<*>? = channel.poll()
                    while (callable != null) {
                        val result = callable.execute()
                        getExceptionOnError("")
                        (callable.future as CompletableFuture<Any>).complete(result)
                        callable = channel.poll()
                    }
                    yield()
                    GPUProfiler.currentTimings = GPUProfiler.currentTask?.dumpTimings() ?: ""
                    GPUProfiler.currentAverages = GPUProfiler.dumpAverages()
                    executeAfterFrameActions()
                    getExceptionOnError("Error in undefined operation")
                }
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
        profiled("Create new fence") {
            createNewGPUFenceForReadState(renderState)
        }
        profiled("Swap buffers") {
            glfwSwapBuffers(window.handle)
        }
    }

    override fun pollEvents() {
        profiled("Poll events") {
            glfwPollEvents()
        }
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


    override fun getExceptionOnError(errorMessage: String): RuntimeException? {
        return this.getExceptionOnError { errorMessage }
    }

    fun checkGLError(errorMessage: () -> String) = execute("checkGLError") {
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
        fun <T> async(start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> T): Deferred<T> {
            val function = { coroutineScope: CoroutineScope,
                             context: CoroutineContext,
                             start: CoroutineStart,
                             block: suspend CoroutineScope.() -> T -> coroutineScope.async(context, start, block) }
            return function(this, coroutineContext, start, block)
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

        private var openGLContextSingleton: OpenGLContext? = null

        @JvmStatic @JvmName("create") operator fun invoke(width: Int, height: Int): OpenGLContext {
            return if(openGLContextSingleton != null) {
                throw IllegalStateException("Can only instantiate one OpenGLContext!")
            } else {
                OpenGLContext(width, height, GlfwWindow(width, height, "HPEngine")).apply {
                    openGLContextSingleton = this
                }
            }
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

    fun onError(block: (errorString: String) -> Unit) {
        val error = getError()
        val isError = error != GL11.GL_NO_ERROR
        if(isError) {
            block(getErrorString(error))
        }
    }

    @JvmOverloads
    fun exceptionOnError(msg: String = "") {
        val error = getError()
        val isError = error != GL11.GL_NO_ERROR
        if(isError) {
            throw IllegalStateException(getErrorString(error) + "\n$msg")
        }
    }
    fun getError(): Int = calculate { GL11.glGetError() }
    fun getErrorString(error: Int) = GLU.gluErrorString(error)
}

inline val isOpenGLThread: Boolean
    get() {
        return Thread.currentThread().isOpenGLThread
    }

val Thread.isOpenGLThread: Boolean
    get() {
        return id == OpenGLContext.Executor.openGLThreadId
    }