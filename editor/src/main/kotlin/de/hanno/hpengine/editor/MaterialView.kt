package de.hanno.hpengine.editor

import com.alee.laf.label.WebLabel
import com.alee.laf.panel.WebPanel
import de.hanno.hpengine.component.ModelComponent
import de.hanno.hpengine.event.EntitySelectedEvent
import de.hanno.hpengine.event.bus.EventBus
import de.hanno.hpengine.renderer.material.Material
import de.hanno.hpengine.util.gui.MaterialView
import net.engio.mbassy.listener.Handler

class MaterialView : WebPanel() {
    init {
        EventBus.getInstance().register(this)
        preferredWidth = 200
    }

    var material : Material? = null

    fun init(material : Material?) {
        this.material = material
        removeAll()
        material?.let {
            add(WebLabel(it.name))
            add(MaterialView(it))
        }
        repaint()
    }

    @Handler
    fun handle(e : EntitySelectedEvent) {
        val modelComponent = e.entity.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)
        init(modelComponent.material)
    }

}