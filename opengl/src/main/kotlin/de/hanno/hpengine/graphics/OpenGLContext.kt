package de.hanno.hpengine.graphics

import CubeMapFace
import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.buffer.*
import de.hanno.hpengine.graphics.buffer.vertex.OpenGLIndexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.VertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.VertexBufferImpl
import de.hanno.hpengine.graphics.buffer.vertex.drawElementsInstancedBaseVertex
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.feature.*
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.glValue
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.IRenderState
import de.hanno.hpengine.graphics.sync.GpuCommandSync
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.*
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import de.hanno.hpengine.toHalfFloat
import glValue
import kotlinx.coroutines.*
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.ARBBindlessTexture.glGetTextureHandleARB
import org.lwjgl.opengl.ARBBindlessTexture.glMakeTextureHandleResidentARB
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL40.*
import org.lwjgl.opengl.GL45.glGetTextureSubImage
import org.lwjgl.system.MemoryStack
import java.awt.geom.Dimension2D
import java.awt.image.BufferedImage
import java.lang.Integer.max
import java.nio.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.min

class OpenGLContext private constructor(
    private val gpuExecutor: GpuExecutor,
    private val config: Config,
    override val profiler: GPUProfiler,
    override val backgroundContext: OpenGLContext? = null,
) : GraphicsApi, GpuExecutor by gpuExecutor {
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    private val capabilities = getCapabilities()

    override fun <T> onGpu(block: context(GraphicsApi)() -> T) = invoke { block(this) }
    internal inline fun <T> onGpuInline(crossinline block: context(GraphicsApi)() -> T) = invoke { block(this) }

    init {
        onGpuInline {
            // TODO: Test whether this does what it is intended to do: binding dummy vertex and index buffers
            VertexBufferImpl(this, EnumSet.of(DataChannels.POSITION3), floatArrayOf(0f, 0f, 0f, 0f)).bind()
            OpenGLIndexBuffer(this).bind()

            cullFace = true
            depthMask = true
            depthTest = true
            depthFunc = DepthFunc.LEQUAL
            blend = false

            enable(Capability.TEXTURE_CUBE_MAP_SEAMLESS)
            glPatchParameteri(GL_PATCH_VERTICES, 3)
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            GL11.glLineWidth(1f)
        }
    }

    private fun getCapabilities() = onGpuInline { GL.getCapabilities() }

    override fun unbindPixelBufferObject() = onGpuInline {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
    }

    override fun setPointsSize(size: Float) {
        GL30.glPointSize(size)
    }

    override val maxLineWidth = onGpuInline { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }
    override fun Texture2D(
        dimension: TextureDimension2D,
        target: TextureTarget,
        internalFormat: InternalTextureFormat,
        textureFilterConfig: TextureFilterConfig,
        wrapMode: WrapMode,
        uploadState: UploadState
    ): OpenGLTexture2D {
        val info = UploadInfo.SingleMipLevelTexture2DUploadInfo(
            dimension = dimension,
            data = null,
            dataCompressed = false,
            srgba = false,
            internalFormat = internalFormat,
            textureFilterConfig = textureFilterConfig,
        )
        val textureAllocationData = allocateTexture(info, TextureTarget.TEXTURE_2D, wrapMode)

        return OpenGLTexture2D(
            dimension,
            textureAllocationData.textureId,
            target,
            internalFormat,
            textureAllocationData.handle,
            textureFilterConfig,
            wrapMode,
            uploadState,
        )
    }

    override fun CubeMap(
        dimension: TextureDimension2D,
        internalFormat: InternalTextureFormat,
        textureFilterConfig: TextureFilterConfig,
        wrapMode: WrapMode,
    ) = OpenGLCubeMap(
        this,
        dimension = dimension,
        filterConfig = textureFilterConfig,
        internalFormat = internalFormat,
        wrapMode = wrapMode,
    )

    override fun FrameBuffer(depthBuffer: DepthBuffer<*>?) = OpenGLFrameBuffer.invoke(this, depthBuffer)
    override val pixelBufferObjectPool = OpenGLPixelBufferObjectPool(this, config)

    override fun createView(texture: Texture2DArray, index: Int): OpenGLTexture2DView {
        val viewTextureId = onGpuInline { glGenTextures() }
        return OpenGLTexture2DView(
            index = index,
            underlying = OpenGLTexture2D(
            dimension = TextureDimension2D(texture.dimension.width, texture.dimension.height),
            id = viewTextureId,
            target = TextureTarget.TEXTURE_2D,
            internalFormat = texture.internalFormat,
            handle =
                if (!isSupported(BindlessTextures)) -1 else onGpuInline { glGetTextureHandleARB(viewTextureId) },
            textureFilterConfig = texture.textureFilterConfig,
            wrapMode = texture.wrapMode,
            uploadState = UploadState.NotUploaded,
        )).apply {
            onGpuInline {
                GL43.glTextureView(
                    id,
                    GL13.GL_TEXTURE_2D,
                    texture.id,
                    internalFormat.glValue,
                    0,
                    texture.mipmapCount + 1,
                    index,
                    1
                )
            }
        }
    }

    override fun createView(texture: CubeMapArray, cubeMapIndex: Int): CubeMap {
        val viewTextureId = onGpuInline { glGenTextures() }
        return object : CubeMap {
            override val dimension = TextureDimension2D(texture.dimension.width, texture.dimension.height)
            override val id = viewTextureId
            override val target = TextureTarget.TEXTURE_CUBE_MAP
            override val internalFormat = texture.internalFormat
            override var handle: Long =
                if (!isSupported(BindlessTextures)) -1 else onGpuInline { glGetTextureHandleARB(viewTextureId) }
            override val textureFilterConfig = texture.textureFilterConfig
            override val wrapMode = texture.wrapMode
            override var uploadState: UploadState = UploadState.Uploaded
        }.apply {
            onGpuInline {
                GL43.glTextureView(
                    id,
                    GL13.GL_TEXTURE_CUBE_MAP,
                    texture.id,
                    internalFormat.glValue,
                    0,
                    texture.mipmapCount + 1,
                    6 * cubeMapIndex,
                    6
                )
            }
        }
    }

    override fun createView(texture: CubeMapArray, cubeMapIndex: Int, faceIndex: Int): Texture2D {
        require(faceIndex in 0..5) { "Face index must identify one of the six cubemap sides" }

        val viewTextureId = onGpuInline { glGenTextures() }
        return object : Texture2D {
            override val dimension = TextureDimension2D(texture.dimension.width, texture.dimension.height)
            override val id = viewTextureId
            override val target = TextureTarget.TEXTURE_2D
            override val internalFormat = texture.internalFormat
            override var handle: Long =
                if (!isSupported(BindlessTextures)) -1 else onGpuInline { glGetTextureHandleARB(viewTextureId) }
            override val textureFilterConfig = texture.textureFilterConfig
            override val wrapMode = texture.wrapMode
            override var uploadState: UploadState = UploadState.Uploaded
        }.apply {
            onGpuInline {
                GL43.glTextureView(
                    id,
                    GL_TEXTURE_2D,
                    texture.id,
                    texture.internalFormat.glValue,
                    0,
                    texture.mipmapCount + 1,
                    6 * cubeMapIndex + faceIndex,
                    1
                )
            }
        }
    }

    private val _registeredRenderTargets = mutableListOf<BackBufferRenderTarget<*>>()
    override val registeredRenderTargets: List<BackBufferRenderTarget<*>> = _registeredRenderTargets

    override var maxTextureUnits = onGpuInline { getMaxCombinedTextureImageUnits() }

    override val isError: Boolean get() = onGpuInline { glGetError() != GL_NO_ERROR }

    override val features = run {
        // TODO: Figure out if this is not supported by my intel gpu
        val bindlessTextures = if (capabilities.GL_ARB_bindless_texture) BindlessTextures else null
        val drawParameters = if (capabilities.GL_ARB_shader_draw_parameters) DrawParameters else null
        val nvShader5 = if (capabilities.GL_NV_gpu_shader5) NvShader5 else null
        val arbShader5 = if (capabilities.GL_ARB_gpu_shader5) ArbShader5 else null
        val amdShaderInt64 = if(capabilities.GL_AMD_gpu_shader_int64) AMDShaderInt64 else null

        listOfNotNull(bindlessTextures, drawParameters, arbShader5, nvShader5, amdShaderInt64)
    }

    override fun createNewGPUFenceForReadState(currentReadState: IRenderState) {
        currentReadState.gpuCommandSync = createCommandSync()
    }

    override fun createCommandSync(onSignaled: (() -> Unit)): OpenGlCommandSync = onGpuInline {
        OpenGlCommandSync(onSignaled).also {
            commandSyncs.add(it)
        }
    }

    override fun launchOnGpu(block: context(GraphicsApi)() -> Unit) = launch { block(this) }

    override fun fencedOnGpu(block: context(GraphicsApi) () -> Unit) {
        val sync = onGpuInline { OpenGlCommandSync() }
        onGpuInline(block)
        onGpuInline {
            sync.await()
        }
    }

    override fun createCommandSync(): OpenGlCommandSync = onGpuInline {
        OpenGlCommandSync().also {
            commandSyncs.add(it)
        }
    }

    private fun getMaxCombinedTextureImageUnits() =
        onGpuInline { glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) }

    private fun getSupportedExtensions(): List<String> = onGpuInline {
        (0 until glGetInteger(GL30.GL_NUM_EXTENSIONS)).mapNotNull { GL30.glGetStringi(GL_EXTENSIONS, it) }
    }

    override fun checkCommandSyncs() = onGpuInline {
        commandSyncs.check()

        val (_, nonSignaled) = commandSyncs.partition { it.signaled }
        commandSyncs = nonSignaled.toMutableList()
    }

    override fun CommandSync(): GpuCommandSync = onGpuInline {
        OpenGlCommandSync().also {
            commandSyncs.add(it)
        }
    }

    override fun CommandSync(onCompletion: () -> Unit): GpuCommandSync = onGpuInline {
        OpenGlCommandSync(onCompletion).also {
            commandSyncs.add(it)
        }
    }

    override fun enable(cap: Capability) = onGpuInline { glEnable(cap.glInt) }
    override fun disable(cap: Capability) = onGpuInline { glDisable(cap.glInt) }

    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        glColorMask(red, green, blue, alpha)
    }

    override fun memoryBarrier() {
        memoryBarrier(Barrier.All)
    }

    override fun memoryBarrier(barrier: Barrier) {
        GL42.glMemoryBarrier(barrier.glValue)
    }

    override var cullFace: Boolean
        get() = Capability.CULL_FACE.isEnabled
        set(value) = onGpuInline { Capability.CULL_FACE.run { if (value) enable(this) else disable(this) } }

    override var cullMode: CullMode
        get() = CullMode.values().first { it.glMode == glGetInteger(GL_CULL_FACE_MODE) }
        set(value) = onGpuInline { glCullFace(value.glMode) }

    override var depthTest: Boolean
        get() = Capability.DEPTH_TEST.isEnabled
        set(value) = onGpuInline { Capability.DEPTH_TEST.run { if (value) enable(this) else disable(this) } }

    override var blend: Boolean
        get() = Capability.BLEND.isEnabled
        set(value) = onGpuInline { Capability.BLEND.run { if (value) enable(this) else disable(this) } }

    override fun activeTexture(textureUnitIndex: Int) {
        val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
        onGpuInline { GL13.glActiveTexture(textureIndexGLInt) }
    }

    private fun getCleanedTextureUnitValue(textureUnit: Int): Int {
        return textureUnit - GL13.GL_TEXTURE0
    }

    private fun getOpenGLTextureUnitValue(textureUnitIndex: Int): Int {
        return GL13.GL_TEXTURE0 + textureUnitIndex
    }

    override fun bindTexture(target: TextureTarget, textureId: Int) {
        onGpuInline {
            glBindTexture(target.glValue, textureId)
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: TextureTarget, textureId: Int) {
        onGpuInline {
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            glBindTexture(target.glValue, textureId)
        }
    }

    override fun bindTextures(textureIds: IntBuffer) {
        onGpuInline { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(count: Int, textureIds: IntBuffer) {
        onGpuInline { GL44.glBindTextures(0, textureIds) }
    }

    override fun bindTextures(firstUnit: Int, count: Int, textureIds: IntBuffer) {
        onGpuInline { GL44.glBindTextures(firstUnit, textureIds) }
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
        onGpuInline {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
        }
    }

    override var depthMask: Boolean
        get() = GlFlag.DEPTH_MASK.enabled
        set(value) = onGpuInline { GlFlag.DEPTH_MASK.run { if (value) enable() else disable() } }

    override var depthFunc: DepthFunc
        get() = DepthFunc.entries.first { it.glFunc == glGetInteger(GL_DEPTH_FUNC) }
        set(value) = onGpuInline { glDepthFunc(value.glFunc) }


    override fun polygonMode(facing: Facing, mode: RenderingMode) = onGpuInline {
        GL11.glPolygonMode(facing.glValue, mode.glValue)
    }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        onGpuInline {
            glReadBuffer(colorAttachment)
        }
    }

    override var blendEquation: BlendMode
        get() = BlendMode.entries.first { it.mode == glGetInteger(GL20.GL_BLEND_EQUATION_RGB) }
        set(value) = onGpuInline { GL14.glBlendEquation(value.mode) }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) = onGpuInline {
        glBlendFunc(sfactor.glValue, dfactor.glValue)
    }

    private val clearColorArray = floatArrayOf(0f, 0f, 0f, 0f)
    private val clearColorVector = org.joml.Vector4f()
    override var clearColor: org.joml.Vector4f
        get() = onGpuInline {
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColorArray).let {
                clearColorVector.apply {
                    x = clearColorArray[0]
                    y = clearColorArray[1]
                    z = clearColorArray[2]
                    w = clearColorArray[3]
                }
            }
        }
        set(value) = onGpuInline { glClearColor(value.x, value.y, value.z, value.w) }

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
        access: Access,
        internalFormat: InternalTextureFormat
    ) {
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access.glValue, internalFormat.glValue)
    }

    override fun genTextures(): Int {
        return onGpuInline { glGenTextures() }
    }

    override val availableVRAM: Int
        get() = onGpuInline { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) }

    override val availableTotalVRAM: Int
        get() = onGpuInline { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) }

    override val dedicatedVRAM: Int
        get() = onGpuInline { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) }

    override val evictedVRAM: Int
        get() = onGpuInline { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX) }

    override val evictionCount: Int
        get() = onGpuInline { glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) }

    override fun createProgramId(): Int = onGpuInline { GL20.glCreateProgram() }

    override fun createShaderId(type: ShaderType): Int = onGpuInline { GL20.glCreateShader(type.glValue) }

    override fun genFrameBuffer() = onGpuInline { glGenFramebuffers() }

    override fun clearCubeMap(id: Int, textureFormat: Int) = onGpuInline {
        glClearTexImage(id, 0, textureFormat, GL_UNSIGNED_BYTE, ZERO_BUFFER_FLOAT)
    }

    override fun clearCubeMapInCubeMapArray(
        textureID: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        cubeMapIndex: Int
    ) = onGpuInline {
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
            ZERO_BUFFER_FLOAT
        )
    }

    override fun texSubImage2D(
        level: Int,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        format: Format,
        type: TexelComponentType,
        pixels: ByteBuffer
    ) = onGpuInline {
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            level, offsetX, offsetY, width, height, format.glValue, type.glValue, pixels
        )
    }

    override fun register(target: BackBufferRenderTarget<*>) {
        if (registeredRenderTargets.any { it.name == target.name } || registeredRenderTargets.contains(target)) return
        _registeredRenderTargets.add(target)
    }

    override fun clearRenderTargets() {
        _registeredRenderTargets.clear()
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
            .appendIfSupported(AMDShaderInt64, "#extension GL_AMD_gpu_shader_int64 : enable")
    }


    override fun isSupported(feature: GpuFeature) = features.contains(feature)

    fun getOpenGlVersionsDefine(): String = "#version 430 core\n"


    override fun bindFrameBuffer(frameBuffer: FrameBuffer) {
        bindFrameBuffer(frameBuffer.frameBuffer)
    }

    override fun finish() = onGpuInline { glFinish() }

    override fun copyImageSubData(
        source: Texture,
        sourceLevel: Int,
        sourceX: Int,
        sourceY: Int,
        sourceZ: Int,
        target: Texture,
        targetLevel: Int,
        targetX: Int,
        targetY: Int,
        targetZ: Int,
        width: Int,
        height: Int,
        depth: Int,
    ) {
        GL43.glCopyImageSubData(
            source.id, source.target.glValue, sourceLevel, sourceX, sourceY, sourceZ,
            target.id, target.target.glValue, targetLevel, targetX, targetY, targetZ,
            width, height, depth
        )
    }

    override fun readPixels(x: Int, y: Int, width: Int, height: Int, format: Format, target: FloatBuffer) {
        glReadPixels(x, y, width, height, format.glValue, GL_FLOAT, target)
    }

    private val currentTextureArrayIndex = AtomicInteger(0)
    override val textureArray: OpenGLTexture2DArray = onGpuInline {
        val textureId = glGenTextures()
        val target = TextureTarget.TEXTURE_2D_ARRAY
        val glTarget = target.glValue

        glBindTexture(glTarget, textureId)
        val filterConfig =
            TextureFilterConfig(minFilter = MinFilter.LINEAR_MIPMAP_LINEAR, MagFilter.LINEAR)
        val wrapMode = WrapMode.Repeat
        texParameters(glTarget, wrapMode, filterConfig)
        val dimension = TextureDimension3D(1024, 1024, 50)
        val internalFormat = InternalTextureFormat.RGBA16F
        texStorage(
            UploadInfo.Texture3DUploadInfo(
                dimension = dimension,
                internalFormat = internalFormat,
                textureFilterConfig = filterConfig,
            ), glTarget
        )

        val handle = getTextureHandle(textureId)

        OpenGLTexture2DArray(
            dimension = dimension,
            id = textureId,
            target = target,
            internalFormat = internalFormat,
            handle = handle,
            textureFilterConfig = filterConfig,
            wrapMode = wrapMode,
            uploadState = UploadState.Uploaded,
            srgba = false,
        )
    }
    override val textureArrayViews = run {
        (0 until 50).map {
            createView(textureArray, it)
        }
    }

    override fun allocateTextureFromArray(
        info: UploadInfo,
        textureTarget: TextureTarget,
        wrapMode: WrapMode,
    ): Texture2D = onGpuInline {
        if(currentTextureArrayIndex.get() < 50
            && info.internalFormat == textureArray.internalFormat
            && (info.dimension as TextureDimension2D).width <= 1024 && (info.dimension as TextureDimension2D).height <= 1024
        ) {
            val index = currentTextureArrayIndex.getAndIncrement()
            val textureView = createView(textureArray, index)
//            generateMipMaps(textureView)
//        TextureAllocationData(textureView.id, textureView.handle, textureView.wrapMode)
            textureView.apply {
                underlying.upload(info as UploadInfo.Texture2DUploadInfo) // TODO: Don't cast here
            }
        } else {
            Texture2D(info as UploadInfo.Texture2DUploadInfo, wrapMode) // TODO: Don't cast here
        }
    }
    override fun allocateTexture(
        info: UploadInfo,
        textureTarget: TextureTarget,
        wrapMode: WrapMode,
    ): TextureAllocationData = onGpuInline {

        val textureId = glGenTextures()
        val glTarget = textureTarget.glValue

        glBindTexture(glTarget, textureId)
        texParameters(glTarget, wrapMode, info.textureFilterConfig)
        texStorage(info, glTarget)

        if (info.textureFilterConfig.minFilter.isMipMapped) {
            GL30.glGenerateMipmap(glTarget)
        }

        val handle = getTextureHandle(textureId)

        TextureAllocationData(textureId, handle, wrapMode)
    }

    override fun Texture2D(
        image: BufferedImage,
        srgba: Boolean,
    ): OpenGLTexture2D {
        val compressInternal = config.performance.textureCompressionByDefault
        val internalFormat = if (compressInternal) {
            if (srgba) InternalTextureFormat.COMPRESSED_SRGB_ALPHA_S3TC_DXT5 else InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5
        } else {
            if (srgba) InternalTextureFormat.SRGB8_ALPHA8 else InternalTextureFormat.RGBA16F
        }

        val precalculateMipMapsOnCpu = true

        val uploadInfo = if(precalculateMipMapsOnCpu) {
            createAllMipLevelsLazyTexture2DUploadInfo(image, internalFormat)
        } else {
            UploadInfo.SingleMipLevelTexture2DUploadInfo(
                TextureDimension(image.width, image.height),
                null, false, srgba,
                internalFormat = internalFormat,
                textureFilterConfig = TextureFilterConfig(),
            )
        }
        return Texture2D(
            uploadInfo,
            WrapMode.Repeat
        )
    }

    override fun Texture2D(
        info: UploadInfo.Texture2DUploadInfo,
        wrapMode: WrapMode
    ): OpenGLTexture2D {
        val textureAllocationData = allocateTexture(
            info,
            TextureTarget.TEXTURE_2D,
            wrapMode
        )
        return OpenGLTexture2D(
            info.dimension,
            textureAllocationData.textureId,
            TextureTarget.TEXTURE_2D,
            info.internalFormat,
            textureAllocationData.handle,
            info.textureFilterConfig,
            wrapMode,
            UploadState.NotUploaded,
            info.srgba,
        ).apply {
            upload(info)
        }
    }

    override fun getTextureHandle(textureId: Int) = if (isSupported(BindlessTextures)) {
        glGetTextureHandleARB(textureId).apply {
            glMakeTextureHandleResidentARB(this)
        }
    } else -1

    override fun FileBasedTexture2D<Texture2D>.uploadAsync() {
        val bufferedImage = ImageIO.read(file).apply { DDSConverter.run { rescaleToNextPowerOfTwo() } }
        (backingTexture as OpenGLTexture2D).upload( // TODO: Try to avoid this cast if possible without going crazy with generics
            createAllMipLevelsLazyTexture2DUploadInfo(
                bufferedImage,
                backingTexture.internalFormat
            )
        )
    }

    fun createAllMipLevelsLazyTexture2DUploadInfo(
        image: BufferedImage,
        internalFormat: InternalTextureFormat
    ): UploadInfo.AllMipLevelsLazyTexture2DUploadInfo {
        val srgba = internalFormat == InternalTextureFormat.SRGB8_ALPHA8 || internalFormat == InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5
        val mipMapSizes = calculateMipMapSizes(image.width, image.height)

        val data = mipMapSizes.map {
            LazyTextureData(it.width, it.height) { image.resize(it.width).toByteBuffer() }
        }
        return UploadInfo.AllMipLevelsLazyTexture2DUploadInfo(
            TextureDimension(image.width, image.height), data, false, srgba,
            internalFormat = internalFormat,
            textureFilterConfig = TextureFilterConfig(),
            mipMapSizes.size,
        )
    }

    internal fun OpenGLTexture2D.upload(info: UploadInfo.Texture2DUploadInfo) {
        if (config.performance.usePixelBufferForTextureUpload) {
            uploadWithPixelBuffer(info)
        } else {
            uploadWithoutPixelBuffer(info)
        }
    }

    private fun OpenGLTexture2D.uploadWithPixelBuffer(info: UploadInfo.Texture2DUploadInfo) {
        pixelBufferObjectPool.scheduleUpload(info, this)
    }

    private fun OpenGLTexture2D.uploadWithoutPixelBuffer( info: UploadInfo.Texture2DUploadInfo) {
        when(info) {
            is UploadInfo.AllMipLevelsTexture2DUploadInfo -> onGpuInline {
                // TODO: Reverse upload from largest mip to smallest
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                var currentWidth = info.dimension.width
                var currentHeight = info.dimension.height
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                info.data.forEachIndexed { index, data ->
                    if (info.dataCompressed) {
                        GL13.glCompressedTexSubImage2D(GL11.GL_TEXTURE_2D, index, 0, 0, currentWidth, currentHeight, info.internalFormat.glValue, data)
                    } else {
                        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, index, 0, 0, currentWidth, currentHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data)
                    }
                    currentWidth = (currentWidth * 0.5).toInt()
                    currentHeight = (currentHeight * 0.5).toInt()
                }
                uploadState = UploadState.Uploaded
            }
            is UploadInfo.SingleMipLevelTexture2DUploadInfo -> onGpuInline {
                val data = info.data
                if(data != null) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                    if (info.dataCompressed) {
                        GL13.glCompressedTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, info.internalFormat.glValue, data)
                    } else {
                        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data)
                    }
                    if(info.textureFilterConfig.minFilter.isMipMapped) {
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                    }
                    uploadState = UploadState.Uploaded
                }
            }
            is UploadInfo.AllMipLevelsLazyTexture2DUploadInfo -> GlobalScope.launch(Dispatchers.IO) {
                info.data.reversed().forEachIndexed { i, textureData ->
                    val mipLevel = info.data.size - i - 1
                    val data = textureData.dataProvider()
                    onGpuInline {
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                        if (info.dataCompressed) {
                            GL13.glCompressedTexSubImage2D(GL11.GL_TEXTURE_2D, mipLevel, 0, 0, textureData.width, textureData.height, info.internalFormat.glValue, data)
                        } else {
                            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, mipLevel, 0, 0, textureData.width, textureData.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data)
                        }
                        when(val uploadState = uploadState) {
                            UploadState.NotUploaded -> this@uploadWithoutPixelBuffer.uploadState = UploadState.Uploading(mipLevel)
                            UploadState.Uploaded -> {}
                            is UploadState.Uploading -> this@uploadWithoutPixelBuffer.uploadState = UploadState.Uploading(min(uploadState.maxMipMapLoaded, mipLevel))
                        }
                    }
                }
                this@uploadWithoutPixelBuffer.uploadState = UploadState.Uploaded
            }
            is UploadInfo.SingleMipLevelsLazyTexture2DUploadInfo -> {
                CompletableFuture.supplyAsync {
                    val data = info.data.dataProvider.invoke()
                    onGpuInline {
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
                        if (info.dataCompressed) {
                            GL13.glCompressedTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, info.internalFormat.glValue, data)
                        } else {
                            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, info.dimension.width, info.dimension.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data)
                        }
                        if(info.textureFilterConfig.minFilter.isMipMapped) {
                            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                        }
                        uploadState = UploadState.Uploaded
                    }
                }
            }
        }

    }

    override fun delete(texture: Texture) = onGpuInline {
        glDeleteTextures(texture.id)
    }
    override fun Program<*>.delete() {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    override fun Program<*>.use() {
        glUseProgram(id)
    }
    override fun Program<*>.unuse() {
        glUseProgram(0)
    }

    override fun Program<*>.getUniformLocation(name: String): Int {
        return GL20.glGetUniformLocation(id, name)
    }

    override fun Program<*>.bindShaderStorageBuffer(index: Int, block: GpuBuffer) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, block.id)
    }

    override fun Program<*>.bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer) {
        GL30.glBindBufferBase(GL42.GL_ATOMIC_COUNTER_BUFFER, index, block.id)
    }

    override fun Program<*>.getShaderStorageBlockIndex(name: String): Int {
        return GL43.glGetProgramResourceIndex(id, GL43.GL_SHADER_STORAGE_BLOCK, name)
    }

    override fun Program<*>.getShaderStorageBlockBinding(name: String, bindingIndex: Int) {
        GL43.glShaderStorageBlockBinding(id, getShaderStorageBlockIndex(name), bindingIndex)
    }

    override fun Program<*>.bind() {
        uniforms.registeredUniforms.forEach {
            it.run {
                when (this) {
                    is Mat4 -> glUniformMatrix4fv(uniformBindings[name]!!.location, false, _value)
                    is Vec3 -> glUniform3f(uniformBindings[name]!!.location, _value.x, _value.y, _value.z)
                    is SSBO -> glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, uniformBindings[name]!!.location, _value.id)
                    is IntType -> glUniform1i(uniformBindings[name]!!.location, _value)
                    is BooleanType -> glUniform1i(uniformBindings[name]!!.location, if(_value) 1 else 0)
                    is FloatType -> glUniform1f(uniformBindings[name]!!.location, _value)
                    is Vec2 -> glUniform2f(uniformBindings[name]!!.location, _value.x, _value.y)
                }
            }
        }
    }
    override fun makeTextureHandleResident(texture: Texture) {
        glMakeTextureHandleResidentARB(texture.handle)
    }

    override fun PersistentShaderStorageBuffer(capacityInBytes: Int) = PersistentMappedBuffer(
        BufferTarget.ShaderStorage,
        capacityInBytes
    )

    override fun PersistentMappedBuffer(
        bufferTarget: BufferTarget,
        capacityInBytes: Int
    ): PersistentMappedBuffer = de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer(
        this,
        bufferTarget,
        capacityInBytes,
    )

    override fun GpuBuffer(
        bufferTarget: BufferTarget,
        capacityInBytes: Int
    ) = OpenGLGpuBuffer(
        this,
        bufferTarget,
        capacityInBytes
    )

    override fun IndexBuffer(graphicsApi: GraphicsApi, intBuffer: IntBuffer) = OpenGLIndexBuffer(graphicsApi, intBuffer)

    private fun texParameters(glTarget: Int, wrapMode: WrapMode, filterConfig: TextureFilterConfig) {
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_S, wrapMode.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_T, wrapMode.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_MIN_FILTER, filterConfig.minFilter.glValue)
        glTexParameteri(glTarget, GL_TEXTURE_MAG_FILTER, filterConfig.magFilter.glValue)
    }

    private fun texStorage(info: UploadInfo, glTarget: Int) = when (info) {
        is UploadInfo.CubeMapArrayUploadInfo -> GL42.glTexStorage3D(
            GL40.GL_TEXTURE_CUBE_MAP_ARRAY,
            info.mipMapCount,
            info.internalFormat.glValue,
            info.dimension.width,
            info.dimension.height,
            info.dimension.depth * 6
        )
        is UploadInfo.CubeMapUploadInfo -> GL42.glTexStorage2D(
            GL40.GL_TEXTURE_CUBE_MAP,
            info.mipMapCount,
            info.internalFormat.glValue,
            info.dimension.width,
            info.dimension.height
        )
        is UploadInfo.Texture2DUploadInfo -> GL42.glTexStorage2D(
            glTarget,
            info.mipMapCount,
            info.internalFormat.glValue,
            info.dimension.width,
            info.dimension.height
        )
        is UploadInfo.Texture3DUploadInfo -> GL42.glTexStorage3D(
            glTarget,
            info.mipMapCount,
            info.internalFormat.glValue,
            info.dimension.width,
            info.dimension.height,
            info.dimension.depth
        )
    }

    override fun generateMipMaps(texture: Texture) = onGpuInline {
        bindTexture(texture)
        GL30.glGenerateMipmap(texture.target.glValue)
    }

    override fun getTextureData(
        texture: Texture,
        mipLevel: Int,
        format: Format,
        texelComponentType: TexelComponentType,
        texels: ByteBuffer
    ): ByteBuffer = onGpuInline {
        bindTexture(texture)
        glGetTexImage(texture.target.glValue, mipLevel, format.glValue, texelComponentType.glValue, texels)
        texels
    }

    override fun getTextureData(
        texture: CubeMap,
        face: CubeMapFace,
        mipLevel: Int,
        format: Format,
        texelComponentType: TexelComponentType,
        texels: ByteBuffer
    ): ByteBuffer = onGpuInline {
        bindTexture(texture)
        glGetTexImage(face.glValue, mipLevel, format.glValue, texelComponentType.glValue, texels)
        texels
    }
    override fun clearTexImage(texture: Texture, format: Format, level: Int, type: TexelComponentType) {
        clearTexImage(texture.id, format, level, type)
    }
    override fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType, buffer: FloatBuffer) = onGpuInline {
        glClearTexImage(textureId, level, format.glValue, type.glValue, buffer)
    }
    override fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType, buffer: ShortBuffer) = onGpuInline {
        glClearTexImage(textureId, level, format.glValue, type.glValue, buffer)
    }
    override fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType) = onGpuInline {
        when(type) {
            TexelComponentType.Float -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_DOUBLE)
            TexelComponentType.UnsignedInt -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_INT)
            TexelComponentType.UnsignedByte -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_BYTE)
            TexelComponentType.HalfFloat -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_HALF_FLOAT)
            TexelComponentType.Int -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_INT)
            TexelComponentType.Byte -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_BYTE)
            TexelComponentType.UnsignedShort -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_SHORT)
            TexelComponentType.UnsignedInt_10_10_10_2 -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_INT)
            TexelComponentType.UnsignedInt_24_8 -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_INT)
        }
    }
    override fun bindImageTexture(unit: Int, texture: Texture, level: Int, layered: Boolean, layer: Int, access: Access) {
        GL42.glBindImageTexture(unit, texture.id, level, layered, layer, access.glValue, texture.internalFormat.glValue)
    }

    override fun getTextureSubImage(cubeMap: CubeMap): FloatArray {
        val floatArray = (0 until 6 * 4).map { 0f }.toFloatArray()
        glGetTextureSubImage(
            cubeMap.id, cubeMap.mipmapCount - 1,
            0, 0, 0, 1, 1, 6, GL_RGBA, GL_FLOAT, floatArray
        )
        return floatArray
    }
    override fun clearColorBuffer(index: Int, floatArray: FloatArray) {
        GL30.glClearBufferfv(GL_COLOR, index, floatArray)
    }
    override fun framebufferDepthTexture(texture: Texture, level: Int) {
        glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, texture.id, level)
    }
    override fun framebufferTextureLayer(index: Int, texture: Texture, level: Int, layer: Int) {
        framebufferTexture(index, texture.target, texture.id, layer, level)
    }
    override fun framebufferTextureLayer(index: Int, textureId: Int, level: Int, layer: Int) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureId, level, layer)
    }
    override fun framebufferDepthTextureLayer(texture: Texture, level: Int, layer: Int) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, texture.id, level, layer)
    }
    override fun framebufferTexture(index: Int, texture: Texture, level: Int) {
        framebufferTexture(index, texture.id, level)
    }
    override fun framebufferTexture(index: Int, textureId: Int, level: Int) {
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureId, level)
    }
    override fun framebufferTexture(index: Int, textureTarget: TextureTarget, textureId: Int, faceIndex: Int, level: Int) {
        when(textureTarget) {
            TextureTarget.TEXTURE_2D -> {
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    textureTarget.glValue,
                    textureId,
                    level
                )
            }
            TextureTarget.TEXTURE_CUBE_MAP -> {
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X + faceIndex,
                    textureId,
                    level
                )
            }
            TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> {
                glFramebufferTexture(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    textureId,
                    level,
                )
            }
            TextureTarget.TEXTURE_2D_ARRAY -> {
                glFramebufferTexture3D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    textureTarget.glValue,
                    textureId,
                    level,
                    0 // TODO: Make it possible to pass layer
                )
            }
            TextureTarget.TEXTURE_3D -> {
                glFramebufferTexture3D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    textureTarget.glValue,
                    textureId,
                    level,
                    0 // TODO: Make it possible to pass layer
                )
            }
        }
    }
    override fun drawBuffers(indices: IntArray) {
        GL20.glDrawBuffers(indices.map { GL_COLOR_ATTACHMENT0 + it }.toIntArray())
    }

    override fun blendFunction(index: Int, sourceFactor: BlendMode.Factor, destinationFactor: BlendMode.Factor) {
        glBlendFunci(index, sourceFactor.glValue, destinationFactor.glValue)
    }
    override fun blendFunctionRGBAlpha(
        index: Int,
        rgbSourceFactor: BlendMode.Factor,
        rgbDestinationFactor: BlendMode.Factor,
        alphaSourceFactor: BlendMode.Factor,
        alphaDestinationFactor: BlendMode.Factor
    ) = glBlendFuncSeparatei(
        index,
        rgbSourceFactor.glValue,
        rgbDestinationFactor.glValue,
        alphaSourceFactor.glValue,
        alphaDestinationFactor.glValue
    )
    override fun drawArraysInstanced(primitiveType: PrimitiveType, firstVertexIndex: Int, count: Int, primitiveCount: Int) {
         glDrawArraysInstanced(
             primitiveType.glValue,
             firstVertexIndex,
             count,
             primitiveCount
         )
    }

    override fun VertexBuffer.draw(indexBuffer: IndexBuffer?): Int {
        bind()
        return if (indexBuffer != null) {
            indexBuffer.bind()
            glDrawElements(GL_TRIANGLES, indexBuffer.buffer)
            (indexBuffer.buffer.capacity() / 3) / Byte.SIZE_BYTES
        } else {
            glDrawArrays(GL_TRIANGLES, 0, verticesCount)
            verticesCount / 3
        }
    }

    override fun validateFrameBufferState(renderTarget: BackBufferRenderTarget<*>) {
        val frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
            val error = when (frameBufferStatus) {
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT"
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT"
                GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> "GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER"
                GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER -> "GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER"
                GL_FRAMEBUFFER_UNSUPPORTED -> "GL_FRAMEBUFFER_UNSUPPORTED"
                GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> "GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE"
                GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS -> "GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS"
                GL_FRAMEBUFFER_UNDEFINED -> "GL_FRAMEBUFFER_UNDEFINED"
                else -> "UNKNOWN"
            }
            throw RuntimeException("Rendertarget ${renderTarget.name} fucked up with $error")
        }
    }

    override fun Shader(
        source: CodeSource,
        defines: Defines,
        shaderType: ShaderType,
    ): Shader = when(shaderType) {
        ShaderType.VertexShader -> VertexShader(this, source, defines)
        ShaderType.TesselationControlShader -> TesselationControlShader(this, source, defines)
        ShaderType.TesselationEvaluationShader -> TesselationEvaluationShader(this, source, defines)
        ShaderType.GeometryShader -> GeometryShader(this, source, defines)
        ShaderType.FragmentShader -> FragmentShader(this, source, defines)
        ShaderType.ComputeShader -> ComputeShader(this, source, defines)
    }.apply {
        load()
    }

    override fun Shader.load() {
        val oldSource = if(source is FileBasedCodeSource) { // TODO: find a better way to do this
            source.source
        } else null

        source.load()

        val resultingShaderSource = source.toResultingShaderSource(defines)


        val shaderLoadFailed = onGpuInline {
            GL20.glShaderSource(id, resultingShaderSource)
            GL20.glCompileShader(id)

            val compileStatus = GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS)
             if (compileStatus == GL_FALSE) {
                System.err.println("Could not compile " + shaderType + ": " + source.name)
                val shaderInfoLog = GL20.glGetShaderInfoLog(id, 10000)
                 System.err.println(shaderInfoLog)
                 // TODO: This is incorrect on intel gpus, shaderlog is structured differently
//                val lines = resultingShaderSource.lines()
//                val lineNumber = Regex("""\d\((.*)\)""").find(shaderInfoLog)!!.groups[1]!!.value.toInt() - 1
//                System.err.println("Problematic line ($lineNumber):")
//                System.err.println(lines[lineNumber])
//                shaderInfoLog = Shader.replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, lines.size)
//                 shaderInfoLog += "\n\n" + resultingShaderSource
//                System.err.println(shaderInfoLog)


                oldSource?.let { oldSource ->
                    val resultingShaderSource = getResultingSource(oldSource, defines)

                    GL20.glShaderSource(id, resultingShaderSource)
                    GL20.glCompileShader(id)
                }

                ShaderLoadException(resultingShaderSource)
            } else {
                null
            }
        }

        shaderLoadFailed?.let { throw it }
    }

    override fun Shader.reload() = try {
        load()
    } catch (_: ShaderLoadException) { }

    override fun Shader.unload() = onGpuInline {
        source.unload()
        GL20.glDeleteShader(id)
    }

    override fun Program<*>.load() = onGpuInline {
        shaders.forEach {
            it.load()
            attach(it)
        }

        bindShaderAttributeChannels()
        linkProgram()
        validateProgram()

        registerUniforms()
    }

    override fun Program<*>.unload() = onGpuInline {
        glDeleteProgram(id)
        shaders.forEach { it.unload() }
    }

    override fun Program<*>.reload() = onGpuInline {
        shaders.forEach { shader ->
            detach(shader)
            try {
                shader.load()
            } catch (e: ShaderLoadException) {
                e.printStackTrace()
            }
            attach(shader)
        }

        bindShaderAttributeChannels()
        linkProgram()
        validateProgram()

        registerUniforms()
    }

    private fun Program<*>.attach(shader: Shader) {
        MemoryStack.stackPush().use {
            val shaderIds = it.callocInt(10)
            GL20.glGetAttachedShaders(this.id, it.callocInt(1).apply { put(0, 10) }, shaderIds)
            var contained = false
            repeat(10) {
                if(shaderIds[it] == shader.id) { contained = true }
            }
            if(!contained) {
                glAttachShader(id, shader.id)
            }
        }
    }

    private fun Program<*>.detach(shader: Shader) {
        MemoryStack.stackPush().use {
            val shaderIds = it.callocInt(10)
            GL20.glGetAttachedShaders(this.id, it.callocInt(1).apply { put(0, 10) }, shaderIds)
            var contained = false
            repeat(10) {
                if(shaderIds[it] == shader.id) { contained = true }
            }
            if(contained) {
                glDetachShader(id, shader.id)
            }
        }
    }

    fun <T : Uniforms> Program<T>.validateProgram() {
        glValidateProgram(id)
        val validationResult = glGetProgrami(id, GL_VALIDATE_STATUS)
        if (GL_FALSE == validationResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program invalid: ${shaders.joinToString { it.name }}")
        }
    }

    private fun Program<*>.bindShaderAttributeChannels() {
        val channels = EnumSet.allOf(DataChannels::class.java)
        for (channel in channels) {
            glBindAttribLocation(id, channel.location, channel.binding)
        }
    }

    fun <T : Uniforms> Program<T>.linkProgram() {
        glLinkProgram(id)
        val linkResult = glGetProgrami(id, GL_LINK_STATUS)
        if (GL_FALSE == linkResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program not linked: ${shaders.joinToString { it.name }}")
        }
    }

    fun Program<*>.registerUniforms() {
        uniformBindings.clear()
        uniforms.registeredUniforms.forEach {
            uniformBindings[it.name] = when(it) {
                is SSBO -> UniformBinding(it.name, it.bindingIndex)
                is BooleanType -> UniformBinding(it.name, getUniformLocation(it.name))
                is FloatType -> UniformBinding(it.name, getUniformLocation(it.name))
                is IntType -> UniformBinding(it.name, getUniformLocation(it.name))
                is Mat4 -> UniformBinding(it.name, getUniformLocation(it.name))
                is Vec3 -> UniformBinding(it.name, getUniformLocation(it.name))
                is Vec2 -> UniformBinding(it.name, getUniformLocation(it.name))
            }
        }
    }

    override fun CodeSource.toResultingShaderSource(defines: Defines): String = getResultingSource(source, defines)

    fun getResultingSource(source: String, defines: Defines) = getOpenGlVersionsDefine() +
            getOpenGlExtensionsDefine() +
            defines.joinToString("\n") { it.defineString } +
            ShaderDefine.getGlobalDefinesString(config) +
            Shader.replaceIncludes(config.directories.engineDir, source, 0).first

    override fun UniformBinding.set(value: Int) = onGpuInline {
        if (location != -1) {
            GL20.glUniform1i(location, value)
        }
    }

    override fun UniformBinding.set(value: Float) = onGpuInline {
        if (location != -1) {
            GL20.glUniform1f(location, value)
        }
    }

    override fun UniformBinding.set(value: Long) = onGpuInline {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64ARB(location, value)
        }
    }

    override operator fun UniformBinding.set(x: Float, y: Float, z: Float) = onGpuInline {
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z)
        }
    }

    override operator fun UniformBinding.set(x: Float, y: Float) = onGpuInline {
        if (location != -1) {
            GL20.glUniform2f(location, x, y)
        }
    }

    override fun UniformBinding.setAsMatrix4(values: FloatBuffer) = onGpuInline {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values)
        }
    }

    override fun UniformBinding.setAsMatrix4(values: ByteBuffer) = onGpuInline {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values.asFloatBuffer())
        }
    }

    override fun UniformBinding.set(values: LongBuffer) = onGpuInline {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64vARB(location, values)
        }
    }

    override fun UniformBinding.setVec3ArrayAsFloatBuffer(values: FloatBuffer) = onGpuInline {
        if (location != -1) {
            GL20.glUniform3fv(location, values)
        }
    }

    override fun UniformBinding.setFloatArrayAsFloatBuffer(values: FloatBuffer) = onGpuInline {
        if (location != -1) {
            GL20.glUniform1fv(location, values)
        }
    }

    override fun dispatchCompute(numGroupsX: Int, numGroupsY: Int, numGroupsZ: Int) = onGpuInline {
        GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS) // TODO: Make this optional
    }

    override fun copyCubeMap(sourceTexture: CubeMap): OpenGLCubeMap {

        val targetTexture  = CubeMap(
            sourceTexture.dimension,
            sourceTexture.internalFormat,
            sourceTexture.textureFilterConfig,
            sourceTexture.wrapMode,
        )

        onGpuInline {
            GL43.glCopyImageSubData(
                sourceTexture.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                targetTexture.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                sourceTexture.dimension.width, sourceTexture.dimension.height, 6
            )
        }

        return targetTexture
    }


    override fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int ,
        textures: List<Texture2D>,
        name: String,
        clear: Vector4f,
    ) = RenderTarget2D(this, RenderTargetImpl(this, frameBuffer, width, height, textures, name, clear))

    override fun <T : Texture> RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<T>,
        name: String,
        clear: Vector4f,
    ) = RenderTargetImpl(
        this,
        frameBuffer,
        width,
        height,
        textures,
        name,
        clear,
    )

    override fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<CubeMapArray>,
        name: String,
        clear: Vector4f,
    ) = CubeMapArrayRenderTarget(
        this, RenderTargetImpl(this, frameBuffer, width, height, textures, name, clear)
    )

    override fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<CubeMap>,
        name: String,
        clear: Vector4f,
    ) = CubeMapRenderTarget(
        this,
        RenderTargetImpl(this, frameBuffer, width, height, textures, name, clear)
    )


    override fun CubeMapArray(
        dimension: TextureDimension3D,
        filterConfig: TextureFilterConfig,
        internalFormat: InternalTextureFormat,
        wrapMode: WrapMode
    ): CubeMapArray {
        val (textureId, handle) = allocateTexture(
            UploadInfo.CubeMapArrayUploadInfo(dimension, internalFormat = internalFormat, textureFilterConfig = filterConfig),
            TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
            wrapMode
        )
        return OpenGLCubeMapArray(
            dimension,
            textureId,
            TextureTarget.TEXTURE_CUBE_MAP_ARRAY,
            internalFormat,
            handle,
            filterConfig,
            wrapMode,
            UploadState.Uploaded
        )
    }
    override fun DepthBuffer(width: Int, height: Int): DepthBuffer<Texture2D> {
        val dimension = TextureDimension(width, height)
        val filterConfig = TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST)
        val textureTarget = TextureTarget.TEXTURE_2D
        val internalFormat = InternalTextureFormat.DEPTH_COMPONENT24
        val (textureId, handle, wrapMode) = allocateTexture(
            UploadInfo.SingleMipLevelTexture2DUploadInfo(
                dimension,
                internalFormat = internalFormat,
                textureFilterConfig = filterConfig
            ),
            textureTarget,
            WrapMode.Repeat,
        )

        return DepthBuffer(
            Texture2D(
                dimension,
                textureTarget,
                internalFormat,
                filterConfig,
                wrapMode,
                UploadState.Uploaded
            )
        )
    }
    override fun <T: Texture> createDepthBuffer(texture: T): DepthBuffer<T> {
        require(texture.internalFormat == InternalTextureFormat.DEPTH_COMPONENT24) {
            "Required format for depth buffer is InternalTextureFormat.DEPTH_COMPONENT24"
        }
        require(!texture.textureFilterConfig.minFilter.isMipMapped) {
            "${texture.textureFilterConfig.minFilter} should not be a mipmapped one"
        }

        return DepthBuffer(texture)
    }

    override fun IndexBuffer.draw(
        drawElementsIndirectCommand: DrawElementsIndirectCommand,
        bindIndexBuffer: Boolean,
        primitiveType: PrimitiveType,
        mode: RenderingMode
    ): TriangleCount = drawElementsInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)

    companion object {
        private var counter = 0
        val OPENGL_THREAD_NAME_BASE = "OpenGLContext"
        fun createOpenGLThreadName() = "${OPENGL_THREAD_NAME_BASE}_${counter++}"

        val ZERO_BUFFER_FLOAT: FloatBuffer = BufferUtils.createFloatBuffer(4).apply {
            put(0f)
            put(0f)
            put(0f)
            put(0f)
            rewind()
        }
        val ZERO_BUFFER_HALF_FLOAT: ShortBuffer = BufferUtils.createShortBuffer(4).apply {
            put(0f.toHalfFloat())
            put(0f.toHalfFloat())
            put(0f.toHalfFloat())
            put(0f.toHalfFloat())
            rewind()
        }
        val ZERO_BUFFER_SHORT = BufferUtils.createShortBuffer(4).apply {
            put(0)
            put(0)
            put(0)
            put(0)
            rewind()
        }
        val ZERO_BUFFER_DOUBLE = BufferUtils.createDoubleBuffer(4).apply {
            put(0.0)
            put(0.0)
            put(0.0)
            put(0.0)
            rewind()
        }
        val RED_BUFFER: FloatBuffer = BufferUtils.createFloatBuffer(4).apply {
            put(1f)
            put(0f)
            put(0f)
            put(1f)
            rewind()
        }

        val ZERO_BUFFER_INT = BufferUtils.createIntBuffer(4).apply {
            put(0)
            put(0)
            put(0)
            put(0)
            rewind()
        }
        val ZERO_BUFFER_BYTE = BufferUtils.createByteBuffer(4).apply {
            put(0)
            put(0)
            put(0)
            put(0)
            rewind()
        }

        private var openGLContextSingleton: OpenGLContext? = null

        operator fun invoke(
            window: Window,
            config: Config,
        ): OpenGLContext = run {
            OpenGLContext(
                window.gpuExecutor,
                config,
                window.profiler,
                window.gpuExecutor.backgroundContext?.let {
                    OpenGLContext(it, config, OpenGLGPUProfiler(config.debug::backgroundContextProfiling))
                }
            ).apply {
                openGLContextSingleton = this
                require(this.parentContext == null) {
                    "Can not set a non main context as singleton!"
                }
            }
        }
    }
}

val Capability.glInt get() = when(this) {
    Capability.TEXTURE_CUBE_MAP_SEAMLESS -> GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
    Capability.DEPTH_TEST -> GL_DEPTH_TEST
    Capability.CULL_FACE -> GL_CULL_FACE
    Capability.BLEND -> GL_BLEND
}

val Capability.isEnabled: Boolean get() = glIsEnabled(glInt)


fun BufferedImage.toByteBuffer(): ByteBuffer {
    val width = width
    val height = height

    val pixels = IntArray(width * height)
    getRGB(0, 0, width, height, pixels, 0, width)

    val buffer = BufferUtils.createByteBuffer(width * height * 4) // 4 because RGBA

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[x + y * width]
            buffer.put((pixel shr 16 and 0xFF).toByte())
            buffer.put((pixel shr 8 and 0xFF).toByte())
            buffer.put((pixel and 0xFF).toByte())
            buffer.put((pixel shr 24 and 0xFF).toByte())
        }
    }

    buffer.flip()
    return buffer
}
