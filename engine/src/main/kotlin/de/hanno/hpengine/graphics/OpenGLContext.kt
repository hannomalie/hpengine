package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.renderer.GLU
import de.hanno.hpengine.graphics.renderer.constants.*
import de.hanno.hpengine.graphics.renderer.rendertarget.IFrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.graphics.state.IRenderState
import de.hanno.hpengine.graphics.vertexbuffer.DataChannels
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.graphics.vertexbuffer.VertexBuffer
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.TextureAllocationData
import de.hanno.hpengine.graphics.texture.UploadInfo
import de.hanno.hpengine.graphics.vertexbuffer.IVertexBuffer
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer.invoke
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer.getPositionsAndTexCoords
import de.hanno.hpengine.scene.VertexIndexBuffer
import kotlinx.coroutines.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.ARBBindlessTexture.glGetTextureHandleARB
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.glGenFramebuffers
import java.lang.Integer.max
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.Executors
import javax.vecmath.Vector2f

class OpenGLContext private constructor(
    override val window: Window,
    val debug: Boolean = false
) : GpuContext, GpuExecutor by window {
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    private val capabilities = getCapabilities()
    private val debugProc = onGpu { if (debug) GLUtil.setupDebugMessageCallback() else null }
    private val dummyVertexIndexBuffer = run {
        val dummyVertexBuffer = VertexBuffer(DEFAULTCHANNELS, floatArrayOf(0f, 0f, 0f, 0f))
        onGpu {
            VertexIndexBuffer(10).apply {
                dummyVertexBuffer.bind()
                indexBuffer.bind()
            }
        }
    }

    init {
        onGpu {
            // Map the internal OpenGL coordinate system to the entire screen
            viewPort(0, 0, window.width, window.height)
            enable(Capability.DEPTH_TEST)
            enable(Capability.CULL_FACE)
            enable(Capability.TEXTURE_CUBE_MAP_SEAMLESS)
            GL40.glPatchParameteri(GL40.GL_PATCH_VERTICES, 3)
        }
    }

    private fun getCapabilities() = onGpu { GL.getCapabilities() }

    override val maxLineWidth = onGpu { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }

    override val registeredRenderTargets = ArrayList<BackBufferRenderTarget<*>>()

    override var maxTextureUnits = onGpu { getMaxCombinedTextureImageUnits() }

    override val fullscreenBuffer = QuadVertexBuffer().apply { upload() }
    override val debugBuffer = QuadVertexBuffer(QuadVertexBuffer.quarterScreenVertices).apply { upload() }
    override val sixDebugBuffers: List<IVertexBuffer> = run {
        val height = -2f / 3f
        val width = 2f
        val widthDiv = width / 6f
        (0..5).map {
            invoke(
                getPositionsAndTexCoords(
                    Vector2f(-1f + it * widthDiv, -1f),
                    Vector2f(-1 + (it + 1) * widthDiv, height)
                )
            ).apply {
                upload()
            }
        }
    }

    override val isError: Boolean get() = onGpu { glGetError() != GL_NO_ERROR }

    override val features = run {
        val bindlessTextures = if (capabilities.GL_ARB_bindless_texture) BindlessTextures else null
        val drawParameters = if (capabilities.GL_ARB_shader_draw_parameters) DrawParameters else null
        val nvShader5 = if (capabilities.GL_NV_gpu_shader5) NvShader5 else null
        val arbShader5 = if (capabilities.GL_ARB_gpu_shader5) ArbShader5 else null

        listOfNotNull(bindlessTextures, drawParameters, arbShader5, nvShader5)
    }

    override fun createNewGPUFenceForReadState(currentReadState: IRenderState) {
        currentReadState.gpuCommandSync = createCommandSync()
    }

    override fun createCommandSync(onSignaled: (() -> Unit)): OpenGlCommandSync = onGpu {
        OpenGlCommandSync(onSignaled).also {
            commandSyncs.add(it)
        }
    }

    override fun <T> onGpu(block: context(GpuContext)() -> T) = invoke { block(this) }

    override fun exceptionOnError() {
        exceptionOnError("")
    }

    override fun createCommandSync(): OpenGlCommandSync = onGpu {
        OpenGlCommandSync().also {
            commandSyncs.add(it)
        }
    }

    private fun getMaxCombinedTextureImageUnits() =
        onGpu { glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) }

    private fun getSupportedExtensions(): List<String> = onGpu {
        (0 until glGetInteger(GL30.GL_NUM_EXTENSIONS)).mapNotNull { GL30.glGetStringi(GL_EXTENSIONS, it) }
    }

    override fun checkCommandSyncs() = onGpu {
        commandSyncs.check()
        val (signaled, nonSignaled) = commandSyncs.partition { it.signaled }
        signaled.forEach {
            it.onSignaled?.invoke()
            it.delete()
        }
        commandSyncs = nonSignaled.toMutableList()
    }

    override fun enable(cap: Capability) = onGpu { cap.enable() }
    override fun disable(cap: Capability) = onGpu { cap.disable() }

    override var cullFace: Boolean
        get() = Capability.CULL_FACE.isEnabled
        set(value) = onGpu { Capability.CULL_FACE.run { if (value) enable() else disable() } }

    override var cullMode: CullMode
        get() = CullMode.values().first { it.glMode == glGetInteger(GL_CULL_FACE_MODE) }
        set(value) = onGpu { glCullFace(value.glMode) }

    override var depthTest: Boolean
        get() = Capability.DEPTH_TEST.isEnabled
        set(value) = onGpu { Capability.DEPTH_TEST.run { if (value) enable() else disable() } }

    override var blend: Boolean
        get() = Capability.BLEND.isEnabled
        set(value) = onGpu { Capability.BLEND.run { if (value) enable() else disable() } }

    override fun activeTexture(textureUnitIndex: Int) {
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
        onGpu { GL13.glActiveTexture(textureIndexGLInt) }
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }

    override fun bindTexture(target: TextureTarget, textureId: Int) {
        onGpu {
            glBindTexture(target.glTarget, textureId)
            getExceptionOnError("")?.let { throw it }
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: TextureTarget, textureId: Int) {
        onGpu {
            getExceptionOnError("beforeBindTexture")?.let { throw it }
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            glBindTexture(target.glTarget, textureId)
            getExceptionOnError("bindTexture")?.let { throw it }
        }
    }

    override fun bindTextures(textureIds: IntBuffer) {
        onGpu { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        onGpu { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        onGpu { GL44.glBindTextures(firstUnit, textureIds) }
    }

    override fun viewPort(x: Int, y: Int, width: Int, height: Int) {
        glViewport(x, y, max(width, 0), max(height, 0))
    }

    override fun clearColorBuffer() {
        glClear(GL_COLOR_BUFFER_BIT)
    }

    override fun clearDepthBuffer() {
        glClear(GL_DEPTH_BUFFER_BIT)
    }

    override fun clearDepthAndColorBuffer() {
        clearDepthBuffer()
        clearColorBuffer()
    }

    override fun bindFrameBuffer(frameBuffer: Int) {
        onGpu {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
        }
    }

    override var depthMask: Boolean
        get() = GlFlag.DEPTH_MASK.enabled
        set(value) = onGpu { GlFlag.DEPTH_MASK.run { if (value) enable() else disable() } }

    override var depthFunc: DepthFunc
        get() = DepthFunc.values().first { it.glFunc == glGetInteger(GL_DEPTH_FUNC) }
        set(value) = onGpu { glDepthFunc(value.glFunc) }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        onGpu {
            glReadBuffer(colorAttachment)
        }
    }

    override var blendEquation: BlendMode
        get() = BlendMode.values().first { it.mode == glGetInteger(GL20.GL_BLEND_EQUATION_RGB) }
        set(value) = onGpu { GL14.glBlendEquation(value.mode) }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) = onGpu {
        glBlendFunc(sfactor.glFactor, dfactor.glFactor)
    }

    private val clearColorArray = floatArrayOf(0f, 0f, 0f, 0f)
    private val clearColorVector = org.joml.Vector4f()
    override var clearColor: org.joml.Vector4f
        get() = onGpu {
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColorArray).let {
                clearColorVector.apply {
                    x = clearColorArray[0]
                    y = clearColorArray[1]
                    z = clearColorArray[2]
                    w = clearColorArray[3]
                }
            }
        }
        set(value) = onGpu { glClearColor(value.x, value.y, value.z, value.w) }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        clearColor = clearColorVector.apply {
            x = r
            y = g
            z = b
            w = a
        }
    }

    override fun bindImageTexture(
        unit: Int,
        textureId: Int,
        level: Int,
        layered: Boolean,
        layer: Int,
        access: Int,
        internalFormat: Int
    ) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat)
    }

    override fun genTextures(): Int {
        return onGpu { glGenTextures() }
    }

    override val availableVRAM: Int
        get() = onGpu { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) }

    override val availableTotalVRAM: Int
        get() = onGpu { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) }

    override val dedicatedVRAM: Int
        get() = onGpu { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) }

    override val evictedVRAM: Int
        get() = onGpu { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX) }

    override val evictionCount: Int
        get() = onGpu { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) }

    inline fun getExceptionOnError(errorMessage: () -> String = { "" }): RuntimeException? {
        if (CHECK_ERRORS) {
            val errorValue = getError()

            if (errorValue != GL_NO_ERROR) {
                val errorString = GLU.gluErrorString(errorValue)
                System.err.println("ERROR: $errorString")
                System.err.println(errorMessage())

                return RuntimeException("$errorString\n${errorMessage()}")
            }
        }
        return null
    }

    override fun createProgramId(): Int = onGpu { GL20.glCreateProgram() }

    override fun genFrameBuffer() = onGpu { glGenFramebuffers() }

    override fun clearCubeMap(textureId: Int, textureFormat: Int) = onGpu {
        glClearTexImage(textureId, 0, textureFormat, GL_UNSIGNED_BYTE, ZERO_BUFFER)
    }

    override fun clearCubeMapInCubeMapArray(
        textureID: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        cubeMapIndex: Int
    ) = onGpu {
        glClearTexSubImage(
            textureID,
            0,
            0,
            0,
            6 * cubeMapIndex,
            width,
            height,
            6,
            internalFormat,
            GL_UNSIGNED_BYTE,
            ZERO_BUFFER
        )
    }

    override fun register(target: BackBufferRenderTarget<*>) {
        if (registeredRenderTargets.any { it.name == target.name } || registeredRenderTargets.contains(target)) return
        registeredRenderTargets.add(target)
    }

    override fun clearRenderTargets() {
        registeredRenderTargets.clear()
    }

    override fun finishFrame(renderState: IRenderState) {
        profiled("Create new fence") {
            createNewGPUFenceForReadState(renderState)
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

    override fun bindFrameBuffer(frameBuffer: IFrameBuffer) {
        bindFrameBuffer(frameBuffer.frameBuffer)
    }

    companion object {
        const val OPENGL_THREAD_NAME = "OpenGLContext"

        val ZERO_BUFFER: FloatBuffer = BufferUtils.createFloatBuffer(4).apply {
            put(0f)
            put(0f)
            put(0f)
            put(0f)
            rewind()
        }
        val RED_BUFFER: FloatBuffer = BufferUtils.createFloatBuffer(4).apply {
            put(1f)
            put(0f)
            put(0f)
            put(1f)
            rewind()
        }

        private var openGLContextSingleton: OpenGLContext? = null

        @JvmStatic
        @JvmName("create")
        operator fun invoke(window: Window): OpenGLContext {
            return if (openGLContextSingleton != null) {
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
        val isError = error != GL_NO_ERROR
        if (isError) {
            block(getErrorString(error))
        }
    }

    fun exceptionOnError(msg: String) {
        val error = getError()
        val isError = error != GL_NO_ERROR
        if (isError) {
            throw IllegalStateException(getErrorString(error) + "\n$msg")
        }
    }

    fun getError(): Int = onGpu { glGetError() }
    fun getErrorString(error: Int) = GLU.gluErrorString(error)
    fun Texture.delete() = onGpu { glDeleteTextures(id) }
    fun finish() = glFinish()

    override fun allocateTexture(
        info: UploadInfo,
        textureTarget: TextureTarget,
        filterConfig: TextureFilterConfig,
        wrapMode: Int,
    ): TextureAllocationData = onGpu {
        val textureId = glGenTextures()
        val glTarget = textureTarget.glTarget

        glBindTexture(glTarget, textureId)
        texParameters(glTarget, wrapMode, filterConfig)
        texStorage(info, glTarget)


        if (filterConfig.minFilter.isMipMapped) {
            GL30.glGenerateMipmap(glTarget)
        }

        val handle = getTextureHandle(textureId)

        TextureAllocationData(textureId, handle, wrapMode).apply {
            exceptionOnError()
        }
    }

    override fun getTextureHandle(textureId: Int) = if (isSupported(BindlessTextures)) {
        glGetTextureHandleARB(textureId)
    } else -1

    private fun texParameters(glTarget: Int, wrapMode: Int, filterConfig: TextureFilterConfig) {
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_S, wrapMode)
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_T, wrapMode)
        glTexParameteri(glTarget, GL_TEXTURE_MIN_FILTER, filterConfig.minFilter.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_MAG_FILTER, filterConfig.magFilter.glValue)
        glTexParameteri(glTarget, GL12.GL_TEXTURE_WRAP_R, wrapMode)
    }

    private fun texStorage(info: UploadInfo, glTarget: Int) = when (info) {
        is UploadInfo.CubeMapArrayUploadInfo -> GL42.glTexStorage3D(
            GL40.GL_TEXTURE_CUBE_MAP_ARRAY,
            info.dimension.getMipMapCount(),
            info.internalFormat,
            info.dimension.width,
            info.dimension.height,
            info.dimension.depth * 6
        )
        is UploadInfo.CubeMapUploadInfo -> GL42.glTexStorage2D(
            GL40.GL_TEXTURE_CUBE_MAP,
            info.dimension.getMipMapCount(),
            info.internalFormat,
            info.dimension.width,
            info.dimension.height
        )
        is UploadInfo.Texture2DUploadInfo -> GL42.glTexStorage2D(
            glTarget,
            info.dimension.getMipMapCount(),
            info.internalFormat,
            info.dimension.width,
            info.dimension.height
        )
        is UploadInfo.Texture3DUploadInfo -> GL42.glTexStorage3D(
            glTarget,
            info.dimension.getMipMapCount(),
            info.internalFormat,
            info.dimension.width,
            info.dimension.height,
            info.dimension.depth
        )
    }
}


class OpenGlExecutorImpl(
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) : CoroutineScope, GpuExecutor {
    var gpuThreadId: Long = runBlocking(dispatcher) {
        Thread.currentThread().name = OpenGLContext.OPENGL_THREAD_NAME
        Thread.currentThread().id
    }

    override val coroutineContext = dispatcher + Job()

    inline val isOpenGLThread: Boolean get() = Thread.currentThread().isOpenGLThread

    inline val Thread.isOpenGLThread: Boolean get() = id == gpuThreadId

    override suspend fun <T> execute(block: () -> T) = if (isOpenGLThread) {
        block()
    } else {
        withContext(coroutineContext) {
            block()
        }
    }

    override fun <RETURN_TYPE> invoke(block: () -> RETURN_TYPE) = if (isOpenGLThread) {
        block()
    } else {
        runBlocking(coroutineContext) {
            block()
        }
    }
}

var DEFAULTCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.TEXCOORD,
    DataChannels.NORMAL
)
var DEFAULTANIMATEDCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.TEXCOORD,
    DataChannels.NORMAL,
    DataChannels.WEIGHTS,
    DataChannels.JOINT_INDICES
)
var DEPTHCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.NORMAL
)
var SHADOWCHANNELS = EnumSet.of(
    DataChannels.POSITION3
)
var POSITIONCHANNEL = EnumSet.of(
    DataChannels.POSITION3
)