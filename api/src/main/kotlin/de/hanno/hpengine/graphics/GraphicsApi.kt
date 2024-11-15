package de.hanno.hpengine.graphics

import CubeMapFace
import de.hanno.hpengine.graphics.constants.Facing
import InternalTextureFormat
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.VertexBuffer
import de.hanno.hpengine.graphics.feature.GpuFeature
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.IRenderState
import de.hanno.hpengine.graphics.sync.GpuCommandSync
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.scene.GeometryBuffer
import kotlinx.coroutines.Job
import org.joml.Vector4f
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer

interface GraphicsApi {
    val profiler: GPUProfiler

    val isError: Boolean

    val availableVRAM: Int

    val availableTotalVRAM: Int

    val dedicatedVRAM: Int

    val evictedVRAM: Int

    val evictionCount: Int

    val maxTextureUnits: Int

    val registeredRenderTargets: List<BackBufferRenderTarget<*>>

    val features: List<GpuFeature>

    fun createNewGPUFenceForReadState(currentReadState: IRenderState)

    fun update(seconds: Float) { }

    var cullFace: Boolean
    var cullMode: CullMode
    var depthTest: Boolean
    var blend: Boolean

    fun enable(cap: Capability)
    fun disable(cap: Capability)
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)

    fun activeTexture(textureUnitIndex: Int)

    fun bindTexture(textureUnitIndex: Int, target: TextureTarget, textureId: Int)

    fun bindTexture(target: TextureTarget, textureId: Int) {
        bindTexture(0, target, textureId)
    }

    fun bindTexture(textureUnitIndex: Int, texture: Texture) {
        bindTexture(textureUnitIndex, texture.target, texture.id)
    }
    fun bindHandle(textureUnitIndex: Int, handle: TextureHandle<*>) {
        when (val texture = handle.texture) {
            null -> {}
            else -> {
                bindTexture(textureUnitIndex, texture)
                setHandleUsageTimeStamp(handle)
            }
        }
    }

    fun bindTexture(texture: Texture) {
        bindTexture(texture.target, texture.id)
    }

    fun setHandleUsageTimeStamp(handle: TextureHandle<*>)
    fun getHandleUsageTimeStamp(handle: TextureHandle<*>): Long?
    fun setHandleUsageDistance(handle: TextureHandle<*>, distance: Float)
    fun getHandleUsageDistance(handle: TextureHandle<*>): Float?

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

    var depthFunc: DepthFunc

    fun readBuffer(colorAttachmentIndex: Int)

    var blendEquation: BlendMode

    fun blendFunc(sfactor: BlendMode.Factor, dfactor: BlendMode.Factor)

    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    var clearColor: Vector4f

    fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Access, internalFormat: InternalTextureFormat)

    fun genTextures(): Int

    fun createProgramId(): Int
    fun createShaderId(type: ShaderType): Int

    fun genFrameBuffer(): Int

    fun clearCubeMap(id: Int, textureFormat: Int)

    fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int)

    fun texSubImage2D(
        level: Int,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        format: Format,
        type: TexelComponentType,
        pixels: ByteBuffer
    )

    fun register(target: BackBufferRenderTarget<*>)

    fun clearRenderTargets()

    fun finishFrame(renderState: IRenderState)

    fun isSupported(feature: GpuFeature): Boolean

    fun isSupported(vararg features: GpuFeature) = isSupported(features.toList())

    fun isSupported(features: List<GpuFeature>): SupportResult {
        features.filter { !isSupported(it) }.let { unsupportedFeatures ->
            return if(unsupportedFeatures.isEmpty()) {
                SupportResult.Supported
            } else SupportResult.UnSupported(unsupportedFeatures)
        }
    }

    fun bindFrameBuffer(frameBuffer: FrameBuffer)
    fun checkCommandSyncs()

    sealed class SupportResult {
        object Supported: SupportResult()
        data class UnSupported(val unsupportedFeatures: List<GpuFeature>): SupportResult()
    }

    fun createCommandSync(): GpuCommandSync
    fun createCommandSync(onSignaled: () -> Unit): GpuCommandSync

    fun CommandSync(): GpuCommandSync
    fun CommandSync(onCompletion: () -> Unit): GpuCommandSync

    fun <T> onGpu(block: context(GraphicsApi)() -> T): T
    fun launchOnGpu(block: context(GraphicsApi)() -> Unit)

    fun fencedOnGpu(block: context(GraphicsApi)() -> Unit)

    val maxLineWidth: Float

    fun Texture2D(
        description: Texture2DDescription
    ): Texture2D

    fun CubeMap(
        dimension: TextureDimension2D,
        internalFormat: InternalTextureFormat,
        textureFilterConfig: TextureFilterConfig,
        wrapMode: WrapMode,
    ): CubeMap

    fun allocateTexture(
        description: TextureDescription,
        textureTarget: TextureTarget,
    ): TextureAllocationData

    fun Texture2D(
        image: BufferedImage,
        srgba: Boolean
    ): Pair<Texture2D, List<ImageData>>

    fun getTextureHandle(textureId: Int): Long

    fun TextureHandle<Texture2D>.uploadAsync(data: List<ImageData>)
    fun FileBasedTexture2D.uploadAsync()

    fun TextureHandle<Texture2D>.upload(data: List<ImageData>)

    fun delete(texture: Texture)

    fun makeTextureHandleResident(texture: Texture)

    fun PersistentShaderStorageBuffer(capacityInBytes: SizeInBytes): GpuBuffer
    fun PersistentMappedBuffer(bufferTarget: BufferTarget, capacityInBytes: SizeInBytes): GpuBuffer

    fun finish()
    fun copyImageSubData(
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
        depth: Int
    )

    fun readPixels(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        format: Format,
        target: FloatBuffer
    )

    fun generateMipMaps(texture: Texture)

    fun getTextureData(texture: Texture, mipLevel: Int, format: Format, texelComponentType: TexelComponentType, texels: ByteBuffer): ByteBuffer
    fun getTextureData(texture: CubeMap, face: CubeMapFace, mipLevel: Int, format: Format, texelComponentType: TexelComponentType, texels: ByteBuffer): ByteBuffer

    fun clearTexImage(texture: Texture, format: Format, level: Int, type: TexelComponentType)
    fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType) // TODO: Remove this version
    fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType, buffer: FloatBuffer) // TODO: Remove this version
    fun clearTexImage(textureId: Int, format: Format, level: Int, type: TexelComponentType, buffer: ShortBuffer) // TODO: Remove this version

    fun bindImageTexture(unit: Int, texture: Texture, level: Int, layered: Boolean, layer: Int, access: Access)
    fun memoryBarrier()
    fun memoryBarrier(barrier: Barrier)
    fun createView(texture: CubeMapArray, cubeMapIndex: Int): CubeMap
    fun createView(texture: CubeMapArray, cubeMapIndex: Int, faceIndex: Int): Texture2D

    // TODO: This currently gets the smallest mipmap level content, adjust for other usecases
    fun getTextureSubImage(cubeMap: CubeMap): FloatArray
    fun clearColorBuffer(index: Int, floatArray: FloatArray)
    fun framebufferDepthTexture(texture: Texture, level: Int)
    fun blendFunction(index: Int, sourceFactor: BlendMode.Factor, destinationFactor: BlendMode.Factor)
    fun blendFunctionRGBAlpha(
        index: Int,
        rgbSourceFactor: BlendMode.Factor,
        rgbDestinationFactor: BlendMode.Factor,
        alphaSourceFactor: BlendMode.Factor,
        alphaDestinationFactor: BlendMode.Factor
    )

    fun drawArraysInstanced(primitiveType: PrimitiveType, firstVertexIndex: ElementCount, count: ElementCount, primitiveCount: ElementCount)

    fun VertexBuffer.draw(indexBuffer: IndexBuffer?): Int
    fun validateFrameBufferState(renderTargetImpl: BackBufferRenderTarget<*>)

    fun framebufferTextureLayer(index: Int, texture: Texture, level: Int, layer: Int)
    fun framebufferTextureLayer(index: Int, textureId: Int, level: Int, layer: Int) // TODO: Remove this
    fun framebufferDepthTextureLayer(texture: Texture, level: Int, layer: Int)
    fun framebufferTexture(index: Int, texture: Texture, level: Int)
    fun framebufferTexture(index: Int, textureId: Int, level: Int) // TODO: Remove this
    fun framebufferTexture(index: Int, textureTarget: TextureTarget, textureId: Int, faceIndex: Int, level: Int) // TODO: Take cubemap here
    fun drawBuffers(indices: IntArray)

    fun Shader(
        source: CodeSource,
        defines: Defines = Defines(),
        shaderType: ShaderType,
    ): Shader

    fun Shader.load()
    fun CodeSource.toResultingShaderSource(defines: Defines): String
    fun Shader.reload()
    fun Shader.unload()
    fun Program<*>.load()
    fun Program<*>.unload()
    fun Program<*>.reload()

    fun UniformBinding.set(value: Int)
    fun UniformBinding.set(value: Float)
    fun UniformBinding.set(value: Long)
    fun UniformBinding.set(x: Float, y: Float, z: Float)
    fun UniformBinding.set(x: Float, y: Float)
    fun UniformBinding.setAsMatrix4(values: FloatBuffer)
    fun UniformBinding.setAsMatrix4(values: ByteBuffer)
    fun UniformBinding.set(values: LongBuffer)
    fun UniformBinding.setVec3ArrayAsFloatBuffer(values: FloatBuffer)
    fun UniformBinding.setFloatArrayAsFloatBuffer(values: FloatBuffer)
    fun dispatchCompute(numGroupsX: Int, numGroupsY: Int, numGroupsZ: Int)
    fun copyCubeMap(sourceTexture: CubeMap): CubeMap
    fun Program<*>.delete()
    fun Program<*>.use()
    fun Program<*>.unuse()
    fun Program<*>.getUniformLocation(name: String): Int
    fun Program<*>.bindShaderStorageBuffer(index: Int, block: GpuBuffer)
    fun Program<*>.bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer)
    fun Program<*>.getShaderStorageBlockIndex(name: String): Int
    fun Program<*>.getShaderStorageBlockBinding(name: String, bindingIndex: Int)
    fun Program<*>.bind()
    fun polygonMode(facing: Facing, mode: RenderingMode)
    fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<Texture2D>,
        name: String,
        clear: Vector4f
    ): RenderTarget2D
    fun <T : Texture> RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<T>,
        name: String,
        clear: Vector4f
    ): RenderTarget

    fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<CubeMapArray>,
        name: String,
        clear: Vector4f
    ): CubeMapArrayRenderTarget

    fun RenderTarget(
        frameBuffer: FrameBuffer,
        width: Int,
        height: Int,
        textures: List<CubeMap>,
        name: String,
        clear: Vector4f
    ): CubeMapRenderTarget


    fun CubeMapArray(
        description: TextureDescription.CubeMapArrayDescription
    ): CubeMapArray


    fun DepthBuffer(width: Int, height: Int): DepthBuffer<Texture2D>
    fun FrameBuffer(depthBuffer: DepthBuffer<*>?): FrameBuffer

    val pixelBufferObjectPool: PixelBufferObjectPool

    fun GpuBuffer(bufferTarget: BufferTarget, capacityInBytes: SizeInBytes): GpuBuffer
    fun IndexBuffer(graphicsApi: GraphicsApi, intBuffer: IntBuffer): IndexBuffer

    fun GeometryBuffer<*>.draw(
        drawElementsIndirectCommand: DrawElementsIndirectCommand,
        bindIndexBuffer: Boolean,
        primitiveType: PrimitiveType,
        mode: RenderingMode
    ): TriangleCount

    fun IndexBuffer.draw(
        drawElementsIndirectCommand: DrawElementsIndirectCommand,
        bindIndexBuffer: Boolean,
        primitiveType: PrimitiveType,
        mode: RenderingMode
    ): TriangleCount

    fun GeometryBuffer<*>.bind()
    fun GeometryBuffer<*>.unbind()

    fun unbindPixelBufferObject()
    fun setPointsSize(size: Float)
    fun <T: Texture> createDepthBuffer(texture: T): DepthBuffer<T>
    fun createView(texture: Texture2DArray, index: Int): Texture2D
    fun update()
    fun updateCpu()
    fun TextureHandle<Texture2D>.uploadWithPixelBuffer(data: List<ImageData>): Job
    fun TextureHandle<Texture2D>.uploadWithoutPixelBuffer(data: List<ImageData>): Job
}

fun GraphicsApi.RenderTarget(
    frameBuffer: FrameBuffer,
    textures: List<Texture2D>,
    name: String,
    clear: Vector4f = Vector4f(),
) = this.RenderTarget(frameBuffer, textures.first().dimension.width, textures.first().dimension.height, textures, name, clear)


typealias TriangleCount = ElementCount

enum class Access {
    ReadOnly,
    WriteOnly,
    ReadWrite
}