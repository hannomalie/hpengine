package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.renderer.picking.Indices
import de.hanno.hpengine.graphics.renderer.picking.OnClickListener
import org.joml.Vector2i
import org.koin.core.annotation.Single

data class EntityClicked(val coordinates: Vector2i, val indices: Indices)

@Single(binds = [EntityClickListener::class, OnClickListener::class])
class EntityClickListener : OnClickListener {
    var clickState: EntityClicked? = null
    override fun onClick(coordinates: Vector2i, indices: Indices) = indices.run {
        if (clickState == null) {
            clickState = EntityClicked(coordinates, indices)
        }
    }

    inline fun <T> consumeClick(onClick: (EntityClicked) -> T): T? = clickState?.let {
        try {
            onClick(it)
        } finally {
            this.clickState = null
        }
    }
}