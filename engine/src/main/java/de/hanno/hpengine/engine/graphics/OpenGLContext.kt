package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
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
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
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
import org.lwjgl.opengl.GL12
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
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.vecmath.Tuple4f
import javax.vecmath.Vector2f
import javax.vecmath.Vector4f

class OpenGLContext private constructor(override val window: Window<OpenGl>, val debug: Boolean = true) : GpuContext<OpenGl>, OpenGlExecutor by window {
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    private val capabilities = getCapabilities()
    private val debugProc = window.invoke { if (debug) GLUtil.setupDebugMessageCallback() else null }
    private lateinit var dummyVertexIndexBuffer: VertexIndexBuffer

    init {
        val dummyVertexBuffer = VertexBuffer(this, ModelComponent.DEFAULTCHANNELS, floatArrayOf(0f,0f,0f,0f))
        window.invoke {
            enable(GlCap.DEPTH_TEST)
            enable(GlCap.CULL_FACE)

            // Map the internal OpenGL coordinate system to the entire screen
            viewPort(0, 0, window.width, window.height)
            dummyVertexIndexBuffer = VertexIndexBuffer(this, 10).apply {
                dummyVertexBuffer.bind()
                indexBuffer.bind()
            }
        }
    }

    private fun getCapabilities() = window { GL.getCapabilities() }

    override val maxLineWidth = window.invoke { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }

    override val backend = object: OpenGl {
        override val gpuContext = this@OpenGLContext
    }

    override val registeredRenderTargets = ArrayList<RenderTarget<*>>()

    override var maxTextureUnits = window { getMaxCombinedTextureImageUnits() }

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

    private val extensions = window.invoke {
        getSupportedExtensions()
    }

    override val isError: Boolean
        get() = window.invoke { GL11.glGetError() != GL11.GL_NO_ERROR }

    override val features = run {
        val bindlessTextures = if(capabilities.GL_ARB_bindless_texture) BindlessTextures else null
        val drawParameters = if(capabilities.GL_ARB_shader_draw_parameters) DrawParameters else null
        val nvShader5 = if(capabilities.GL_NV_gpu_shader5) NvShader5 else null
        val arbShader5 = if(capabilities.GL_ARB_gpu_shader5) ArbShader5 else null

        listOfNotNull(bindlessTextures, drawParameters, arbShader5, nvShader5)
    }

    override fun createNewGPUFenceForReadState(currentReadState: RenderState) {
        currentReadState.gpuCommandSync = createCommandSync()
    }

    override fun createCommandSync(onSignaled: (() -> Unit)): OpenGlCommandSync = createCommandSync(onSignaled)
    override fun createCommandSync(): OpenGlCommandSync = createCommandSyncImpl()

    private fun createCommandSyncImpl(onSignaled: (() -> Unit)? = null): OpenGlCommandSync = window.invoke {
        OpenGlCommandSync(onSignaled).also {
            commandSyncs.add(it)
        }
    }

    private fun getMaxCombinedTextureImageUnits() = window.invoke { GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) }

    private fun getSupportedExtensions(): List<String> = window.invoke {
        (0 until GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS)).mapNotNull { GL30.glGetStringi(GL11.GL_EXTENSIONS, it) }
    }

    override fun update(seconds: Float) { }

    override fun checkCommandSyncs() {
        val signaledJustNow = commandSyncs.check()

        window.invoke {
            commandSyncs.filter { it.signaled }.forEach { it.delete() }
        }
        commandSyncs = commandSyncs.filter { !it.signaled }.toMutableList()

        signaledJustNow.forEach {
            it.onSignaled?.invoke()
        }
    }

    override fun enable(cap: GlCap) = window.invoke { cap.enable() }
    override fun disable(cap: GlCap) = window.invoke { cap.disable() }

    override var cullFace: Boolean
        get() = GlCap.CULL_FACE.enabled
        set(value) = window.invoke { GlCap.CULL_FACE.run { if(value) enable() else disable() } }

    override var cullMode: CullMode
        get() = CullMode.values().first { it.glMode == GL11.glGetInteger(GL_CULL_FACE_MODE) }
        set(value) = window.invoke { GL11.glCullFace(value.glMode) }

    override var depthTest: Boolean
        get() = GlCap.DEPTH_TEST.enabled
        set(value) = window.invoke { GlCap.DEPTH_TEST.run { if(value) enable() else disable() } }

    override var blend: Boolean
        get() = GlCap.BLEND.enabled
        set(value) = window.invoke { GlCap.BLEND.run { if(value) enable() else disable() } }

    override fun activeTexture(textureUnitIndex: Int) {
        if(textureUnitIndex < 0) { throw IllegalArgumentException("Passed textureUnitIndex of < 0") }
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
        window.invoke { GL13.glActiveTexture(textureIndexGLInt) }
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }
    override fun bindTexture(target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        window.invoke {
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("")?.let{ throw it }
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: GlTextureTarget, textureId: Int) {
        if(textureId < 0) { throw IllegalArgumentException("Passed textureId of < 0") }
        window.invoke {
            getExceptionOnError("beforeBindTexture")?.let{ throw it }
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            GL11.glBindTexture(target.glTarget, textureId)
            getExceptionOnError("bindTexture")?.let{ throw it }
        }
    }

    override fun bindTextures(textureIds: IntBuffer) {
        window.invoke { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        window.invoke { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        window.invoke { GL44.glBindTextures(firstUnit, textureIds) }
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
        window.invoke {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
        }
    }

    override var depthMask: Boolean
        get() = GlFlag.DEPTH_MASK.enabled
        set(value) = window.invoke { GlFlag.DEPTH_MASK.run { if(value) enable() else disable() } }

    override var depthFunc: GlDepthFunc
        get() = GlDepthFunc.values().first { it.glFunc == GL11.glGetInteger(GL_DEPTH_FUNC) }
        set(value) = window.invoke { glDepthFunc(value.glFunc) }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        window.invoke {
            GL11.glReadBuffer(colorAttachment)
        }
    }

    override var blendEquation: BlendMode
        get() = BlendMode.values().first { it.mode == GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) }
        set(value) = window.invoke { GL14.glBlendEquation(value.mode) }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) = window.invoke {
        GL11.glBlendFunc(sfactor.glFactor, dfactor.glFactor)
    }

    private val clearColorArray = floatArrayOf(0f,0f,0f,0f)
    private val clearColorVector = Vector4f()
    override var clearColor: Tuple4f
        get() = window.invoke { GL11.glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColorArray).let { clearColorVector.apply { set(clearColorArray) } } }
        set(value) = window.invoke { GL11.glClearColor(value.x, value.y, value.z, value.w) }

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
        return window.invoke { GL11.glGenTextures() }
    }

    override val availableVRAM: Int
        get() = window.invoke { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) }

    override val availableTotalVRAM: Int
        get() = window.invoke { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) }

    override val dedicatedVRAM: Int
        get() = window.invoke { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) }

    override val evictedVRAM: Int
        get() = window.invoke { GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX) }

    override val evictionCount: Int
        get() = window.invoke{ GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) }

    inline fun getExceptionOnError(errorMessage: () -> String = { "" }): RuntimeException? {
        if (GpuContext.CHECKERRORS) {
            val errorValue = getError()

            if (errorValue != GL11.GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                return RuntimeException("$errorString\n${errorMessage()}")
            }
        }
        return null
    }

    override fun createProgramId(): Int = window.invoke { GL20.glCreateProgram() }

    override fun destroy() {
        try {
            window.shutdown()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }


    override fun genFrameBuffer() = window.invoke { glGenFramebuffers() }

    override fun clearCubeMap(textureId: Int, textureFormat: Int) = window.invoke {
        glClearTexImage(textureId, 0, textureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int) = window.invoke {
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
                .appendIfSupported(ArbShaderInt64, "#extension GL_ARB_gpu_shader_int64 : enable")
                .appendIfSupported(BindlessTextures, "#extension GL_ARB_bindless_texture : enable")
                .appendIfSupported(DrawParameters, "#extension GL_ARB_shader_draw_parameters : enable")
    }

    override fun isSupported(feature: GpuFeature) = features.contains(feature)

    fun getOpenGlVersionsDefine(): String = "#version 430 core\n"


    override fun getExceptionOnError(errorMessage: String): RuntimeException? {
        return getExceptionOnError { errorMessage }
    }

    override fun bindFrameBuffer(frameBuffer: FrameBuffer) {
        bindFrameBuffer(frameBuffer.frameBuffer)
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
    fun getError(): Int = window.invoke { GL11.glGetError() }
    fun getErrorString(error: Int) = GLU.gluErrorString(error)
    fun Texture.delete() = invoke { GL11.glDeleteTextures(id) }
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

    inline val isOpenGLThread: Boolean
        get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean
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

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE): RETURN_TYPE {
        if(isOpenGLThread) return block()

        return runBlocking(coroutineContext) {
            block()
        }
    }

    override fun shutdown() { }
}
