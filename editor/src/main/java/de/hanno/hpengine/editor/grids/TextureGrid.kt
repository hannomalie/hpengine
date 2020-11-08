package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.model.texture.FileBasedCubeMap
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.Scene
import net.miginfocom.swing.MigLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class TextureGrid(private val gpuContext: GpuContext<*>,
                  val texture: Texture): JPanel() {

    init {
        layout = MigLayout("wrap 2")
        labeled("Dimensions", JLabel(texture.dimension.toString()))
        if(texture is FileBasedCubeMap) {
            add(JButton("Load").apply {
                addActionListener {
                    texture.load(gpuContext)
                }
            })
        }
    }
}
