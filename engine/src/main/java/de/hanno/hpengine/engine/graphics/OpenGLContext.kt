package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.constants.GlFlag
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.util.commandqueue.FutureCallable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE
import org.lwjgl.opengl.GL11.GL_CULL_FACE_MODE
import org.lwjgl.opengl.GL11.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.GLUtil
import org.lwjgl.opengl.NVXGPUMemoryInfo
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.ArrayList
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.vecmath.Tuple4f
import javax.vecmath.Vector2f
import javax.vecmath.Vector4f
import kotlin.coroutines.CoroutineContext

class OpenGLContext private constructor(override val window: Window<OpenGl>) : GpuContext<OpenGl>, OpenGlExecutor by window {
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    init {
        privateInit()
    }

    private lateinit var capabilities: GLCapabilities
    override val backend = object: OpenGl {
        override val gpuContext = this@OpenGLContext
    }

    override val registeredRenderTargets = ArrayList<RenderTarget<*>>()

    internal val channel = Channel<FutureCallable<*>>(Channel.UNLIMITED)

    override var isInitialized = false
        private set
    override var maxTextureUnits: Int = 0

    override val fullscreenBuffer = QuadVertexBuffer(this, true).apply { upload() }
    override val debugBuffer = QuadVertexBuffer(this, false).apply { upload() }
    override val sixDebugBuffers: List<VertexBuffer> = run {
        val height = -2f / 3f
        val width = 2f
        val widthDiv = width / 6f
        (0..5).map {
            val quadVertexBuffer = QuadVertexBuffer(this, Vector2f(-1f + it * widthDiv, -1f), Vector2f(-1 + (it + 1) * widthDiv, height))
            quadVertexBuffer.upload()
            quadVertexBuffer
        }
    }
    private lateinit var extensions: String

    override val isError: Boolean
        get() {
            return window.calculateX(Callable{ GL11.glGetError() != GL11.GL_NO_ERROR })!!
        }


    override val features = run {
        val bindlessTextures = if(capabilities.GL_ARB_bindless_texture) BindlessTextures else null
        val drawParameters = if(capabilities.GL_ARB_shader_draw_parameters) DrawParameters else null
        val nvShader5 = if(capabilities.GL_NV_gpu_shader5) NvShader5 else null
        val arbShader5 = if(capabilities.GL_ARB_gpu_shader5) ArbShader5 else null

        listOfNotNull(bindlessTextures, drawParameters, arbShader5)
    }

    override fun createNewGPUFenceForReadState(currentReadState: RenderState) {
        currentReadState.gpuCommandSync = createCommandSync()
    }

    private fun createCommandSync(): OpenGlCommandSync = OpenGlCommandSync().apply {
        commandSyncs.add(this)
    }

    internal fun privateInit() {
        window.execute {
            capabilities = GL.getCapabilities()

            val numExtensions = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS)
            val supportedExtensions = (0 until numExtensions).map { GL30.glGetStringi(GL11.GL_EXTENSIONS, it) }

            extensions = supportedExtensions.joinToString(" ")

            val debug = true
            if (debug) {
                val debugProc = GLUtil.setupDebugMessageCallback()
            }

            enable(GlCap.DEPTH_TEST)
            enable(GlCap.CULL_FACE)

            // Map the internal OpenGL coordinate system to the entire screen
            viewPort(0, 0, window.width, window.height)
            maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)

            isInitialized = true

        }
    }

    override fun update(seconds: Float) { }

    override fun checkCommandSyncs() {
        commandSyncs = checkCommandSyncsReturnUnsignaled(commandSyncs)
    }

    override fun enable(cap: GlCap) {
        cap.enable()
    }

    override fun disable(cap: GlCap) {
        cap.disable()
    }

    override var cullFace: Boolean
        get() = GlCap.CULL_FACE.enabled
        set(value) = GlCap.CULL_FACE.run { if(value) enable() else disable() }

    override var cullMode: CullMode
        get() = CullMode.values().first { it.glMode == GL11.glGetInteger(GL_CULL_FACE_MODE) }
        set(value) = GL11.glCullFace(value.glMode)

    override var depthTest: Boolean
        get() = GlCap.DEPTH_TEST.enabled
        set(value) = GlCap.DEPTH_TEST.run { if(value) enable() else disable() }

    override var blend: Boolean
        get() = GlCap.BLEND.enabled
        set(value) = GlCap.BLEND.run { if(value) enable() else disable() }

    override fun activeTexture(textureUnitIndex: Int) {
        if(textureUnitIndex < 0) { throw IllegalArgumentException("Passed textureUnitIndex of < 0") }
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
        window.execute { GL13.glActiveTexture(textureIndexGLInt) }
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }
    override fun bindTexture(target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        window.execute {
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("")?.let{ throw it }
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        window.execute {
            getExceptionOnError("beforeBindTexture")?.let{ throw it }
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("bindTexture")?.let{ throw it }
        }
    }

    override fun bindTextures(textureIds: IntBuffer) {
        window.execute { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        window.execute { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        window.execute { GL44.glBindTextures(firstUnit, textureIds) }
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
        window.execute {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
        }
    }

    override var depthMask: Boolean
        get() = GlFlag.DEPTH_MASK.enabled
        set(value) = GlFlag.DEPTH_MASK.run { if(value) enable() else disable() }

    override var depthFunc: GlDepthFunc
        get() = GlDepthFunc.values().first { it.glFunc == GL11.glGetInteger(GL_DEPTH_FUNC) }
        set(value) { glDepthFunc(value.glFunc) }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        GL11.glReadBuffer(colorAttachment)
    }

    override var blendEquation: BlendMode
        get() = BlendMode.values().first { it.mode == GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) }
        set(value) { GL14.glBlendEquation(value.mode) }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) {
        GL11.glBlendFunc(sfactor.glFactor, dfactor.glFactor)
    }

    private val clearColorArray = floatArrayOf(0f,0f,0f,0f)
    private val clearColorVector = Vector4f()
    override var clearColor: Tuple4f
        get() = GL11.glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColorArray).let { clearColorVector.apply { set(clearColorArray) } }
        set(value) = GL11.glClearColor(value.x, value.y, value.z, value.w)

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        clearColor = clearColorVector.apply {
            x = r
            y = g
            z = b
            w = a
        }
    }

    override fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Int, internalFormat: Int) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat)
    }

    override fun genTextures(): Int {
        return window.calculateX(Callable { GL11.glGenTextures() })!!
    }

    override val availableVRAM: Int
        get() = window.calculateX(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) })!!

    override val availableTotalVRAM: Int
        get() = window.calculateX(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) })!!

    override val dedicatedVRAM: Int
        get() = window.calculateX(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) })!!

    override val evictedVRAM: Int
        get() = window.calculateX(Callable { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX) })!!

    override val evictionCount: Int
        get() = window.calculateX(Callable{ GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) })!!

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

    override fun createProgramId(): Int {
        return window.calculateX(Callable{ GL20.glCreateProgram() })!!
    }

    override fun destroy() {
        try {
            window.shutdown()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

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
        return window.calculateX(Callable{ glGenFramebuffers() })!!
    }

    override fun clearCubeMap(textureId: Int, textureFormat: Int) {
        glClearTexImage(textureId, 0, textureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int) {
        glClearTexSubImage(textureID, 0, 0, 0, 6 * cubeMapIndex, width, height, 6, internalFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun register(target: RenderTarget<*>) {
        registeredRenderTargets.add(target)
    }

    override fun finishFrame(renderState: RenderState) {
        profiled("Create new fence") {
            createNewGPUFenceForReadState(renderState)
        }
        profiled("Swap buffers") {
            window.swapBuffers()
        }
    }

    fun getOpenGlExtensionsDefine(): String {

        fun String.appendIfSupported(feature: GpuFeature, string: String): String {
            val supported = isSupported(feature)
            val defineString = if (supported) "#define ${feature.defineString.toUpperCase()} true\n" else ""
            val featureStringOrEmpty = if (supported) " $string\n" else ""
            return "${this}$featureStringOrEmpty$defineString"
        }
        return "".appendIfSupported(NvShader5, "#extension GL_NV_gpu_shader5 : enable")
                .appendIfSupported(ArbShader5, "#extension GL_ARB_gpu_shader5 : enable")
                .appendIfSupported(BindlessTextures, "#extension GL_ARB_bindless_texture : enable")
                .appendIfSupported(DrawParameters, "#extension GL_ARB_shader_draw_parameters : enable")
    }

    override fun isSupported(feature: GpuFeature) = features.contains(feature)

    fun getOpenGlVersionsDefine(): String = "#version 430 core\n"


    override fun getExceptionOnError(errorMessage: String): RuntimeException? {
        return this.getExceptionOnError { errorMessage }
    }

    override fun bindFrameBuffer(frameBuffer: FrameBuffer) {
        bindFrameBuffer(frameBuffer.frameBuffer)
    }
    fun checkGLError(errorMessage: () -> String) = window.execute() {
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

        @JvmStatic @JvmName("create") operator fun invoke(window: Window<OpenGl>): OpenGLContext {
            return if(openGLContextSingleton != null) {
                throw IllegalStateException("Can only instantiate one OpenGLContext!")
            } else {
                OpenGLContext(window).apply {
                    openGLContextSingleton = this
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
    fun getError(): Int = window.calculate { GL11.glGetError() }
    fun getErrorString(error: Int) = GLU.gluErrorString(error)
    fun Texture.delete() = execute { GL11.glDeleteTextures(id) }
    fun finish() = GL11.glFinish()

}


class OpenGlExecutorImpl(val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    : CoroutineScope, OpenGlExecutor {
    override var openGLThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = OpenGLContext.OPENGL_THREAD_NAME
        Thread.currentThread().id
    }

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

    inline val isOpenGLThread: Boolean
        get() = Thread.currentThread().isOpenGLThread

    val Thread.isOpenGLThread: Boolean
        get() = id == openGLThreadId

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

    override suspend fun <T> execute(block: () -> T): T {
        if(isOpenGLThread) return block()

        return withContext(coroutineContext) {
            block()
        }
    }

    override fun execute(runnable: Runnable) {

        if(isOpenGLThread) return runnable.run()

        return runBlocking(coroutineContext) {
            runnable.run()
        }
    }

    override fun <RETURN_TYPE> calculateX(callable: Callable<RETURN_TYPE>): RETURN_TYPE {
        if(isOpenGLThread) {
            return callable.call()
        }
        return runBlocking(coroutineContext) {
            callable.call()
        }
    }

    override fun shutdown() {
    }
}
