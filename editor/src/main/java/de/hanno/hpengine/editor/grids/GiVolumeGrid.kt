package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import org.joml.Vector3fc
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty0

class GiVolumeGrid(val giVolumeComponent: GIVolumeComponent, val engineContext: EngineContext, val sceneManager: SceneManager) : JPanel() {
    init {
        layout = MigLayout("wrap 2")
        add(JButton("Use cam").apply {
            addActionListener {
                sceneManager.scene.activeCamera = giVolumeComponent.orthoCam
            }
        })
        add(JButton("Restore scene cam").apply {
            addActionListener {
                sceneManager.scene.restoreWorldCamera()
            }
        })
        addAABBInput()
        labeled("Resolution", JFormattedTextField(giVolumeComponent.giVolumeGrids.albedoGrid.dimension.width).apply {
            this@apply.addPropertyChangeListener {
                if(it.propertyName == "value" && it.newValue != it.oldValue) {
                    val oldVolumeGrids = giVolumeComponent.giVolumeGrids
                    giVolumeComponent.giVolumeGrids = engineContext.textureManager.createGIVolumeGrids(this.value as Int)
                    engineContext.textureManager.run {
                        gpuContext.invoke {
                            gpuContext.finish()
                            oldVolumeGrids.albedoGrid.delete()
                            oldVolumeGrids.normalGrid.delete()
                            oldVolumeGrids.indexGrid.delete()
                        }
                    }
                    engineContext.config.debug.isForceRevoxelization = true
                }
            }
        })
    }

    private fun addAABBInput() {
        labeled("Min", giVolumeComponent.boundingVolume::localMin.toInput())
        labeled("Max", giVolumeComponent.boundingVolume::localMax.toInput())
    }

}

private fun KMutableProperty0<Vector3fc>.toInput(): JComponent = JFormattedTextField(this.get()).apply {
    columns = 5
    val xInput = JFormattedTextField(this@toInput.get().x).apply {
        columns = 5
        addPropertyChangeListener("value") {
            val newVector = Vector3f(this@toInput.get())
            newVector.x = it.newValue as Float
            this@toInput.set(newVector)
        }
    }
    val yInput = JFormattedTextField(this@toInput.get().y).apply {
        columns = 5
        addPropertyChangeListener("value") {
            val newVector = Vector3f(this@toInput.get())
            newVector.y = it.newValue as Float
            this@toInput.set(newVector)
        }
    }
    val zInput = JFormattedTextField(this@toInput.get().z).apply {
        columns = 5
        addPropertyChangeListener("value") {
            val newVector = Vector3f(this@toInput.get())
            newVector.z = it.newValue as Float
            this@toInput.set(newVector)
        }
    }
    return JPanel().apply {
        labeled("X", xInput)
        labeled("Y", yInput)
        labeled("Z", zInput)
    }
}

fun Vector3f.toInput(): JPanel {
    val xInput = JFormattedTextField(this@toInput.x).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.x = it.newValue as Float }
    }
    val yInput = JFormattedTextField(this@toInput.y).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.y = it.newValue as Float }
    }
    val zInput = JFormattedTextField(this@toInput.z).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.z = it.newValue as Float }
    }
    return JPanel().apply {
        labeled("X", xInput)
        labeled("Y", yInput)
        labeled("Z", zInput)
    }
}
