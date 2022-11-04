package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.glTarget
import de.hanno.hpengine.graphics.renderer.constants.glValue
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D.TextureUploadInfo.*
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class CubeMapFileDataFormat {
    Single,
    Six
}

data class TextureAllocationData(val textureId: Int, val internalFormat: Int, val handle: Long)

fun allocateTexture(
    gpuContext: GpuContext,
    info: OpenGLTexture2D.TextureUploadInfo,
    textureTarget: TextureTarget,
    filterConfig: TextureFilterConfig = TextureFilterConfig(),
    internalFormat: Int,
    wrapMode: Int = GL12.GL_REPEAT
): TextureAllocationData = gpuContext.invoke {
    val textureId = glGenTextures()
    val glTarget = textureTarget.glTarget
    glBindTexture(glTarget, textureId)
    glTexParameteri(glTarget, GL_TEXTURE_WRAP_S, wrapMode)
    glTexParameteri(glTarget, GL_TEXTURE_WRAP_T, wrapMode)
    glTexParameteri(glTarget, GL_TEXTURE_MIN_FILTER, filterConfig.minFilter.glValue)
    glTexParameteri(glTarget, GL_TEXTURE_MAG_FILTER, filterConfig.magFilter.glValue)
//        TODO: Remove casts
    when (textureTarget) {
        TextureTarget.TEXTURE_2D -> {
            val info = info as Texture2DUploadInfo
            GL42.glTexStorage2D(
                glTarget,
                info.dimension.getMipMapCount(),
                internalFormat,
                info.dimension.width,
                info.dimension.height
            )
        }
        TextureTarget.TEXTURE_2D_ARRAY, TextureTarget.TEXTURE_3D -> {
            val info = info as Texture3DUploadInfo
            GL42.glTexStorage3D(
                glTarget,
                info.dimension.getMipMapCount(),
                internalFormat,
                info.dimension.width,
                info.dimension.height,
                info.dimension.depth
            )
            glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
        }
        TextureTarget.TEXTURE_CUBE_MAP -> {
            val info = info as Texture2DUploadInfo
            GL42.glTexStorage2D(
                GL40.GL_TEXTURE_CUBE_MAP,
                info.dimension.getMipMapCount(),
                internalFormat,
                info.dimension.width,
                info.dimension.height
            )
            glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
        }
        TextureTarget.TEXTURE_CUBE_MAP_ARRAY -> {
            val info = info as Texture3DUploadInfo
            GL42.glTexStorage3D(
                GL40.GL_TEXTURE_CUBE_MAP_ARRAY,
                TextureDimension2D(info.dimension.width, info.dimension.height).getMipMapCount(),
                internalFormat,
                info.dimension.width,
                info.dimension.height,
                info.dimension.depth * 6
            )
            glTexParameteri(glTarget, GL_TEXTURE_WRAP_R, wrapMode)
        }
    }
    if (filterConfig.minFilter.isMipMapped) {
        GL30.glGenerateMipmap(glTarget)
    }

    val handle = if (gpuContext.isSupported(BindlessTextures)) {
        ARBBindlessTexture.glGetTextureHandleARB(textureId)
    } else -1

    TextureAllocationData(textureId, internalFormat, handle)
}

fun ByteBuffer.buffer(values: ByteArray) {
    order(ByteOrder.nativeOrder())
    put(values, 0, values.size)
    flip()
}
