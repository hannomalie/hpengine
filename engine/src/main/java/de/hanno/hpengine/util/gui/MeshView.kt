package de.hanno.hpengine.util.gui

import com.alee.extended.panel.GridPanel
import com.alee.extended.panel.WebComponentPanel
import com.alee.laf.combobox.WebComboBox
import com.alee.laf.label.WebLabel
import com.alee.laf.panel.WebPanel
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import java.awt.event.ActionListener
import java.util.*
import javax.swing.JScrollPane

class MeshView(val engine: Engine<*>, val mesh: Mesh<*>): WebPanel() {
    init {
        isUndecorated = true
        this.setSize(600, 700)
        setMargin(20)
        init(mesh)
    }

    private fun init(mesh: Mesh<*>) {

        val materialSelectionPanel = WebComponentPanel()
        materialSelectionPanel.setElementMargin(4)
        val meshName = WebLabel(mesh.name)
        materialSelectionPanel.addElement(meshName)
        addMaterialSelect(materialSelectionPanel, ActionListener { e ->
            val cb = e.source as WebComboBox
            val selectedMaterial = engine.scene.materialManager.materials[cb.selectedIndex]
            mesh.material = selectedMaterial
            engine.eventBus.post(MaterialChangedEvent())
        }, mesh.material)


        val meshPanel = GridPanel(1, 1, materialSelectionPanel)
        val meshesPanel = JScrollPane(meshPanel)
        add(meshesPanel)
    }

    private fun addMaterialSelect(webComponentPanel: WebComponentPanel, actionListener: ActionListener, initialSelection: SimpleMaterial) {
        val materialSelect = WebComboBox(Vector<SimpleMaterial>(engine.scene.materialManager.materials))

        materialSelect.selectedIndex = engine.scene.materialManager.materials.indexOf(initialSelection)
        materialSelect.addActionListener(actionListener)
        webComponentPanel.addElement(materialSelect)
    }
}