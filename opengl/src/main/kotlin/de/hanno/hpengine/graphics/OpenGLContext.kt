package de.hanno.hpengine.graphics

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.buffer.vertex.OpenGLIndexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.VertexBufferImpl
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.feature.*
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.glValue
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.IRenderState
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.ressources.*
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import glValue
import kotlinx.coroutines.*
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.ARBBindlessTexture.glGetTextureHandleARB
import org.lwjgl.opengl.ARBBindlessTexture.glMakeTextureHandleResidentARB
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBClearTexture.glClearTexSubImage
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL40.glBlendFuncSeparatei
import org.lwjgl.opengl.GL40.glBlendFunci
import org.lwjgl.opengl.GL45.glGetTextureSubImage
import java.lang.Integer.max
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*

context(GPUProfiler)
class OpenGLContext private constructor(
    override val window: Window,
    private val config: Config,
) : GraphicsApi, GpuExecutor by window {
    private var commandSyncs: MutableList<OpenGlCommandSync> = ArrayList(10)
    private val capabilities = getCapabilities()

    init {
        onGpu {
            // TODO: Test whether this does what it is intended to do: binding dummy vertex and index buffers
            VertexBufferImpl(EnumSet.of(DataChannels.POSITION3), floatArrayOf(0f, 0f, 0f, 0f)).bind()
            OpenGLIndexBuffer().bind()

            // Map the internal OpenGL coordinate system to the entire screen
            viewPort(0, 0, window.width, window.height)

            cullFace = true
            depthMask = true
            depthTest = true
            depthFunc = DepthFunc.LEQUAL
            blend = false

            enable(Capability.TEXTURE_CUBE_MAP_SEAMLESS)
            GL40.glPatchParameteri(GL40.GL_PATCH_VERTICES, 3)
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            GL11.glLineWidth(1f)
        }
    }

    private fun getCapabilities() = onGpu { GL.getCapabilities() }

    override val maxLineWidth = onGpu { GL12.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE) }
    override fun Texture2D(
        dimension: TextureDimension2D,
        target: TextureTarget,
        internalFormat: InternalTextureFormat,
        textureFilterConfig: TextureFilterConfig,
        wrapMode: WrapMode,
        uploadState: UploadState
    ): OpenGLTexture2D {
        val info = UploadInfo.Texture2DUploadInfo(
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
        dimension = dimension,
        filterConfig = textureFilterConfig,
        internalFormat = internalFormat,
        wrapMode = wrapMode,
    )

    override fun FrameBuffer(depthBuffer: DepthBuffer<*>?) = OpenGLFrameBuffer.invoke(depthBuffer)
    override val pixelBufferObjectPool = config.run { OpenGLPixelBufferObjectPool() }

    override fun createView(texture: CubeMapArray, cubeMapIndex: Int): CubeMap {
        val viewTextureId = onGpu { glGenTextures() }
        return object: CubeMap {
            override val dimension = TextureDimension2D(texture.dimension.width, texture.dimension.height)
            override val id = viewTextureId
            override val target = TextureTarget.TEXTURE_CUBE_MAP
            override val internalFormat = texture.internalFormat
            override var handle: Long = glGetTextureHandleARB(viewTextureId)
            override val textureFilterConfig = texture.textureFilterConfig
            override val wrapMode = texture.wrapMode
            override var uploadState: UploadState = UploadState.Uploaded
        }.apply {
            onGpu {
                GL43.glTextureView(
                    id,
                    GL13.GL_TEXTURE_CUBE_MAP,
                    texture.id,
                    internalFormat.glValue,
                    0,
                    texture.mipmapCount,
                    6 * cubeMapIndex,
                    6
                )
            }
        }
    }

    override fun createView(texture: CubeMapArray, cubemapIndex: Int, faceIndex: Int): Texture2D {
        require(faceIndex in 0..5) { "Face index must identify one of the six cubemap sides" }

        val viewTextureId = onGpu { glGenTextures() }
        return object: Texture2D {
            override val dimension = TextureDimension2D(texture.dimension.width, texture.dimension.height)
            override val id = viewTextureId
            override val target = TextureTarget.TEXTURE_CUBE_MAP
            override val internalFormat = texture.internalFormat
            override var handle: Long = glGetTextureHandleARB(viewTextureId)
            override val textureFilterConfig = texture.textureFilterConfig
            override val wrapMode = texture.wrapMode
            override var uploadState: UploadState = UploadState.Uploaded
        }.apply {
            GL43.glTextureView(
                id,
                GL_TEXTURE_2D,
                texture.id,
                texture.internalFormat.glValue,
                0,
                1,
                6 * cubemapIndex + faceIndex,
                1
            )
        }
    }

    override val registeredRenderTargets = ArrayList<BackBufferRenderTarget<*>>()

    override var maxTextureUnits = onGpu { getMaxCombinedTextureImageUnits() }

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

    override fun <T> onGpu(block: context(GraphicsApi)() -> T) = invoke { block(this) }

    override fun <T> onGpu(priority: Int, block: context(GraphicsApi) () -> T): T = runBlocking {
        async(priority) {
            block(this@OpenGLContext)
        }.await()
    }
    override fun fencedOnGpu(block: context(GraphicsApi) () -> Unit) {
        val sync = onGpu { OpenGlCommandSync() }
        onGpu(block)
        onGpu {
            sync.await()
        }
    }
    override fun fencedOnGpu(priority: Int, block: context(GraphicsApi) () -> Unit) {
        val sync = onGpu { OpenGlCommandSync() }
        onGpu(priority, block)
        onGpu {
            sync.await()
        }
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

    override fun enable(cap: Capability) = onGpu { glEnable(cap.glInt) }
    override fun disable(cap: Capability) = onGpu { glDisable(cap.glInt) }

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
        set(value) = onGpu { Capability.CULL_FACE.run { if (value) enable(this) else disable(this) } }

    override var cullMode: CullMode
        get() = CullMode.values().first { it.glMode == glGetInteger(GL_CULL_FACE_MODE) }
        set(value) = onGpu { glCullFace(value.glMode) }

    override var depthTest: Boolean
        get() = Capability.DEPTH_TEST.isEnabled
        set(value) = onGpu { Capability.DEPTH_TEST.run { if (value) enable(this) else disable(this) } }

    override var blend: Boolean
        get() = Capability.BLEND.isEnabled
        set(value) = onGpu { Capability.BLEND.run { if (value) enable(this) else disable(this) } }

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
            glBindTexture(target.glValue, textureId)
        }
    }

    override fun bindTexture(textureUnitIndex: Int, target: TextureTarget, textureId: Int) {
        onGpu {
            val textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex)
            GL13.glActiveTexture(textureIndexGLInt)
            glBindTexture(target.glValue, textureId)
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


    override fun polygonMode(facing: Facing, mode: RenderingMode) = onGpu {
        GL11.glPolygonMode(facing.glValue, mode.glValue)
    }

    override fun readBuffer(colorAttachmentIndex: Int) {
        val colorAttachment = GL_COLOR_ATTACHMENT0 + colorAttachmentIndex
        onGpu {
            glReadBuffer(colorAttachment)
        }
    }

    override var blendEquation: BlendMode
        get() = BlendMode.values().first { it.mode == glGetInteger(GL20.GL_BLEND_EQUATION_RGB) }
        set(value) = onGpu { GL14.glBlendEquation(value.mode) }

    override fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor) = onGpu {
        glBlendFunc(sfactor.glValue, dfactor.glValue)
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
        access: Access,
        internalFormat: InternalTextureFormat
    ) {
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access.glValue, internalFormat.glValue)
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

    override fun createProgramId(): Int = onGpu { GL20.glCreateProgram() }

    override fun createShaderId(type: ShaderType): Int = onGpu { GL20.glCreateShader(type.glValue) }

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

    override fun texSubImage2D(
        level: Int,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        format: Format,
        type: TexelComponentType,
        pixels: ByteBuffer
    ) {
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            level, offsetX, offsetY, width, height, format.glValue, type.glValue, pixels
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


    override fun bindFrameBuffer(frameBuffer: FrameBuffer) {
        bindFrameBuffer(frameBuffer.frameBuffer)
    }

    override fun finish() = onGpu { glFinish() }

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
    override fun allocateTexture(
        info: UploadInfo,
        textureTarget: TextureTarget,
        wrapMode: WrapMode,
    ): TextureAllocationData = onGpu {
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

    override fun getTextureHandle(textureId: Int) = if (isSupported(BindlessTextures)) {
        glGetTextureHandleARB(textureId).apply {
            glMakeTextureHandleResidentARB(this)
        }
    } else -1

    override fun delete(texture: Texture) = onGpu {
        glDeleteTextures(texture.id)
    }
    override fun Program<*>.delete() {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    override fun Program<*>.use() {
        glUseProgram(id)
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
                    is Mat4 -> GL20.glUniformMatrix4fv(uniformBindings[name]!!.location, false, _value)
                    is Vec3 -> GL20.glUniform3f(uniformBindings[name]!!.location, _value.x, _value.y, _value.z)
                    is SSBO -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, uniformBindings[name]!!.location, _value.id)
                    is IntType -> GL20.glUniform1i(uniformBindings[name]!!.location, _value)
                    is BooleanType -> GL20.glUniform1i(uniformBindings[name]!!.location, if(_value) 1 else 0)
                    is FloatType -> GL20.glUniform1f(uniformBindings[name]!!.location, _value)
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
        capacityInBytes: Int,
        bufferTarget: BufferTarget
    ) = PersistentMappedBuffer(
        bufferTarget,
        capacityInBytes
    )
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

    override fun generateMipMaps(texture: Texture) = onGpu {
        bindTexture(texture)
        GL30.glGenerateMipmap(texture.target.glValue)
    }

    override fun getTextureData(texture: Texture, mipLevel: Int, format: Format, texels: ByteBuffer): ByteBuffer {
        bindTexture(texture)
        glGetTexImage(texture.target.glValue, mipLevel, format.glValue, GL_UNSIGNED_BYTE, texels)
        return texels
    }
    override fun clearTexImage(texture: Texture, format: Format, level: Int, type: TexelComponentType) {
        clearTexImage(texture.id, format, level, type)
    }
    override fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType, floatBuffer: FloatBuffer) = onGpu {
        glClearTexImage(textureId, level, format.glValue, type.glValue, floatBuffer)
    }
    override fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType) = onGpu {
        when(type) {
            TexelComponentType.Float -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER)
            TexelComponentType.Int -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_INT)
            TexelComponentType.UnsignedByte -> glClearTexImage(textureId, level, format.glValue, type.glValue, ZERO_BUFFER_BYTE)
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
        framebufferTexture(index, texture.id, level, layer)
    }
    override fun framebufferTextureLayer(index: Int, textureId: Int, level: Int, layer: Int) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureId, level, layer)
    }
    override fun framebufferTexture(index: Int, texture: Texture, level: Int) {
        framebufferTexture(index, texture.id, level)
    }
    override fun framebufferTexture(index: Int, textureId: Int, level: Int) {
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, textureId, level)
    }
    override fun framebufferTexture(index: Int, textureId: Int, faceIndex: Int, level: Int) {
        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0 + index,
            GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + faceIndex,
            textureId,
            level
        )
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
    override fun drawArraysInstanced(primitiveType: PrimitiveType, firsIndex: Int, count: Int, primitiveCount: Int) {
         glDrawArraysInstanced(
             primitiveType.glValue,
             firsIndex,
             count,
             primitiveCount
         )
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

        source.load()

        val resultingShaderSource = source.toResultingShaderSource(defines)

        onGpu {
            GL20.glShaderSource(id, resultingShaderSource)
            GL20.glCompileShader(id)
        }

        val shaderLoadFailed = onGpu {
            val shaderStatus = GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS)
            if (shaderStatus == GL11.GL_FALSE) {
                System.err.println("Could not compile " + shaderType + ": " + source.name)
                var shaderInfoLog = GL20.glGetShaderInfoLog(id, 10000)
                val lines = resultingShaderSource.lines()
                System.err.println("Could not compile " + shaderType + ": " + source.name)
                System.err.println("Problematic line:")
                System.err.println(lines[Regex("""\d\((.*)\)""").find(shaderInfoLog)!!.groups[1]!!.value.toInt()-1])
                shaderInfoLog = Shader.replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, lines.size)
                System.err.println(resultingShaderSource)
                ShaderLoadException(shaderInfoLog)
            } else null
        }

        shaderLoadFailed?.let { throw it }
    }

    override fun Shader.reload() {
        try {
            load()
        } catch (_: ShaderLoadException) { }
    }
    override fun Shader.unload() {
        source.unload()
        GL20.glDeleteShader(id)
    }

    override fun AbstractProgram<*>.load() {
        onGpu {
            shaders.forEach {
                it.load()
                attach(it)
            }

            bindShaderAttributeChannels()
            linkProgram()
            validateProgram()

            registerUniforms()
        }
    }
    override fun AbstractProgram<*>.unload() {
        onGpu {
            glDeleteProgram(id)
            shaders.forEach { it.unload() }
        }
    }

    override fun AbstractProgram<*>.reload() {
        onGpu {
            try {
                shaders.forEach {
                    it.load()
                    attach(it)
                }

                bindShaderAttributeChannels()
                linkProgram()
                validateProgram()

                registerUniforms()
            } catch (_: ShaderLoadException) { }
        }
    }
    // TODO: Make reload not destructive
    override fun AbstractProgram<*>.reloadProgram(): Unit = try {
        onGpu {
            shaders.forEach {
                detach(it)
                it.reload()
            }

            load()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    private fun AbstractProgram<*>.attach(shader: Shader) {
        glAttachShader(id, shader.id)
    }

    private fun AbstractProgram<*>.detach(shader: Shader) {
        glDetachShader(id, shader.id)
    }

    fun <T : Uniforms> AbstractProgram<T>.validateProgram() {
        glValidateProgram(id)
        val validationResult = glGetProgrami(id, GL_VALIDATE_STATUS)
        if (GL11.GL_FALSE == validationResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program invalid: $name")
        }
    }

    private fun AbstractProgram<*>.bindShaderAttributeChannels() {
        val channels = EnumSet.allOf(DataChannels::class.java)
        for (channel in channels) {
            glBindAttribLocation(id, channel.location, channel.binding)
        }
    }

    fun <T : Uniforms> AbstractProgram<T>.linkProgram() {
        glLinkProgram(id)
        val linkResult = glGetProgrami(id, GL_LINK_STATUS)
        if (GL11.GL_FALSE == linkResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program not linked: $name")
        }
    }

    fun AbstractProgram<*>.registerUniforms() {
        uniformBindings.clear()
        uniforms.registeredUniforms.forEach {
            uniformBindings[it.name] = when(it) {
                is SSBO -> UniformBinding(it.name, it.bindingIndex)
                is BooleanType -> UniformBinding(it.name, getUniformLocation(it.name))
                is FloatType -> UniformBinding(it.name, getUniformLocation(it.name))
                is IntType -> UniformBinding(it.name, getUniformLocation(it.name))
                is Mat4 -> UniformBinding(it.name, getUniformLocation(it.name))
                is Vec3 -> UniformBinding(it.name, getUniformLocation(it.name))
            }
        }
    }

    override fun CodeSource.toResultingShaderSource(defines: Defines): String = getOpenGlVersionsDefine() +
            getOpenGlExtensionsDefine() +
            defines.joinToString("\n") { it.defineString } +
            ShaderDefine.getGlobalDefinesString(config) +
            Shader.replaceIncludes(config.directories.engineDir, source, 0).first

    override fun UniformBinding.set(value: Int) {
        if (location != -1) {
            GL20.glUniform1i(location, value)
        }
    }

    override fun UniformBinding.set(value: Float) {
        if (location != -1) {
            GL20.glUniform1f(location, value)
        }
    }

    override fun UniformBinding.set(value: Long) {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64ARB(location, value)
        }
    }

    override operator fun UniformBinding.set(x: Float, y: Float, z: Float) {
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z)
        }
    }

    override operator fun UniformBinding.set(x: Float, y: Float) {
        if (location != -1) {
            GL20.glUniform2f(location, x, y)
        }
    }

    override fun UniformBinding.setAsMatrix4(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values)
        }
    }

    override fun UniformBinding.setAsMatrix4(values: ByteBuffer) {
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, values.asFloatBuffer())
        }
    }

    override fun UniformBinding.set(values: LongBuffer) {
        if (location != -1) {
            ARBBindlessTexture.glUniformHandleui64vARB(location, values)
        }
    }

    override fun UniformBinding.setVec3ArrayAsFloatBuffer(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniform3fv(location, values)
        }
    }

    override fun UniformBinding.setFloatArrayAsFloatBuffer(values: FloatBuffer) {
        if (location != -1) {
            GL20.glUniform1fv(location, values)
        }
    }

    override fun dispatchCompute(numGroupsX: Int, numGroupsY: Int, numGroupsZ: Int) {
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

        GL43.glCopyImageSubData(
            sourceTexture.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
            targetTexture.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
            sourceTexture.dimension.width, sourceTexture.dimension.height, 6
        )

        return targetTexture
    }


    override fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int ,
        textures: List<Texture2D>,
        name: String,
        clear: Vector4f,
    ) = RenderTarget2D(RenderTargetImpl(frameBuffer, width, height, textures, name, clear))

    override fun <T : Texture> RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<T>,
        name: String,
        clear: Vector4f,
    ) = RenderTargetImpl(
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
        RenderTargetImpl(frameBuffer, width, height, textures, name, clear)
    )

    override fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<CubeMap>,
        name: String,
        clear: Vector4f,
    ) = CubeMapRenderTarget(
        RenderTargetImpl(frameBuffer, width, height, textures, name, clear)
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
            UploadInfo.Texture2DUploadInfo(dimension, internalFormat = internalFormat, textureFilterConfig = filterConfig),
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
            profiler: OpenGLGPUProfiler,
            window: Window,
            config: Config,
        ): OpenGLContext = if (openGLContextSingleton != null) {
            throw IllegalStateException("Can only instantiate one OpenGLContext!")
        } else {
            profiler.run {
                OpenGLContext(window, config).apply {
                    openGLContextSingleton = this
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

