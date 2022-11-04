package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.renderer.constants.*
import de.hanno.hpengine.graphics.renderer.rendertarget.IFrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.state.IRenderState
import de.hanno.hpengine.graphics.vertexbuffer.IVertexBuffer
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.TextureAllocationData
import de.hanno.hpengine.graphics.texture.UploadInfo
import org.joml.Vector4f
import java.nio.IntBuffer

interface GpuContext {

    val window: Window

    val isError: Boolean

    val availableVRAM: Int

    val availableTotalVRAM: Int

    val dedicatedVRAM: Int

    val evictedVRAM: Int

    val evictionCount: Int

    val maxTextureUnits: Int

    val fullscreenBuffer: IVertexBuffer
    val debugBuffer: IVertexBuffer
    val sixDebugBuffers: List<IVertexBuffer>

    val registeredRenderTargets: List<RenderTarget<*>>

    val features: List<GpuFeature>

    fun createNewGPUFenceForReadState(currentReadState: IRenderState)

    fun update(seconds: Float) { }

    var cullFace: Boolean
    var cullMode: CullMode
    var depthTest: Boolean
    var blend: Boolean

    fun enable(cap: Capability)
    fun disable(cap: Capability)

    fun activeTexture(textureUnitIndex: Int)

    fun bindTexture(textureUnitIndex: Int, target: TextureTarget, textureId: Int)

    fun bindTexture(target: TextureTarget, textureId: Int) {
        bindTexture(0, target, textureId)
    }

    fun bindTexture(textureUnitIndex: Int, texture: Texture) {
        bindTexture(textureUnitIndex, texture.target, texture.id)
    }

    fun bindTexture(texture: Texture) {
        bindTexture(texture.target, texture.id)
    }

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

    fun bindImageTexture(unit: Int, textureId: Int, level: Int, layered: Boolean, layer: Int, access: Int, internalFormat: Int)

    fun genTextures(): Int

    fun createProgramId(): Int

    fun genFrameBuffer(): Int

    fun clearCubeMap(i: Int, textureFormat: Int)

    fun clearCubeMapInCubeMapArray(textureID: Int, internalFormat: Int, width: Int, height: Int, cubeMapIndex: Int)

    fun register(target: RenderTarget<*>)

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

    fun getExceptionOnError(errorMessage: String = ""): RuntimeException?
    fun bindFrameBuffer(frameBuffer: IFrameBuffer)
    fun checkCommandSyncs()

    sealed class SupportResult {
        object Supported: SupportResult()
        data class UnSupported(val unsupportedFeatures: List<GpuFeature>): SupportResult()
    }

    fun createCommandSync(): GpuCommandSync
    fun createCommandSync(onSignaled: () -> Unit): GpuCommandSync
    operator fun <T> invoke(block: () -> T): T
    fun exceptionOnError()

    val maxLineWidth: Float

    fun allocateTexture(
        info: UploadInfo,
        textureTarget: TextureTarget,
        filterConfig: TextureFilterConfig = TextureFilterConfig(),
        internalFormat: Int,
        wrapMode: Int,
    ): TextureAllocationData
}