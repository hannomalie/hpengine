package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL12
import java.awt.Color
import java.awt.FlowLayout
import java.nio.ByteOrder
import java.awt.image.DataBufferByte
import java.util.Hashtable
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.Raster
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.ComponentColorModel
import java.awt.image.ColorModel
import java.awt.image.WritableRaster
import java.io.File
import java.nio.ByteBuffer
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel


class TextureManagerTest {
}

fun main(args: Array<String>) {
    val eventBus = MBassadorEventBus()
    val gpuContext = OpenGLContext()
    val programManager = ProgramManager(gpuContext, eventBus)
    val textureManager = TextureManager(eventBus, programManager, gpuContext)

//    val texture = textureManager.getTexture("hp/assets/textures/stone_diffuse.png")
    val texture = textureManager.getTexture("hp/assets/models/textures/Sponza_Curtain_Blue_diffuse.png")

    val bufferedImage = textureManager.loadImageAsStream("hp/assets/models/textures/Sponza_Curtain_Blue_diffuse.png")
    val frame = JFrame()
    frame.getContentPane().setLayout(FlowLayout())
    frame.getContentPane().add(JLabel(ImageIcon(bufferedImage)))
    frame.pack()
    frame.setVisible(true)

    val byteBuffer = convertImageData(bufferedImage)

    gpuContext.execute {
        gpuContext.bindTexture(0, texture)
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texture.dimension.width, texture.dimension.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer)
    }

    val result = DrawResult(FirstPassResult(), SecondPassResult())
    val renderState = RenderState(gpuContext)

    val renderer = SimpleTextureRenderer(programManager, texture)

    while(true) {
        gpuContext.execute {
            gpuContext.frontBuffer.use(true)
            renderer.render(result, renderState)
        }
    }

}

fun convertImageData(bufferedImage: BufferedImage): ByteBuffer {
    val imageBuffer: ByteBuffer
    val raster: WritableRaster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
            bufferedImage.width, bufferedImage.height, 4, null)
    val texImage: BufferedImage

    val glAlphaColorModel = ComponentColorModel(ColorSpace
            .getInstance(ColorSpace.CS_sRGB), intArrayOf(8, 8, 8, 8),
            true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)

    texImage = BufferedImage(glAlphaColorModel, raster, true,
            Hashtable<Any, Any>())

    // copy the source image into the produced image
    val g = texImage.graphics
    g.color = Color(0f, 0f, 0f, 0f)
    g.fillRect(0, 0, 256, 256)
    g.drawImage(bufferedImage, 0, 0, null)

    // build a byte buffer from the temporary image
    // that be used by OpenGL to produce a texture.
    val data = (texImage.raster.dataBuffer as DataBufferByte)
            .data

    imageBuffer = ByteBuffer.allocateDirect(data.size)
    imageBuffer.order(ByteOrder.nativeOrder())
    imageBuffer.put(data, 0, data.size)
    imageBuffer.flip()

    return imageBuffer
}