package de.hanno.hpengine.editor

import com.alee.laf.label.WebLabel
import com.alee.laf.panel.WebPanel
import de.hanno.hpengine.engine.model.Entity
import de.hanno.hpengine.event.EntitySelectedEvent
import de.hanno.hpengine.event.bus.EventBus
import net.engio.mbassy.listener.Handler

class EntityView : WebPanel() {
    init {
        EventBus.getInstance().register(this)
        preferredWidth = 200
    }
    var entity : Entity? = null

    fun init(entity : Entity?) {
        this.entity = entity
        removeAll()
        entity?.let {
            add(WebLabel(entity.name))
        }
        repaint()
    }

    @Handler
    fun handle(e : EntitySelectedEvent) {
        init(e.entity)
    }

}