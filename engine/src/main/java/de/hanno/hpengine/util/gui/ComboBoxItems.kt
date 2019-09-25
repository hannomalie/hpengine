package de.hanno.hpengine.util.gui

import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget

class DirectTextureOutputItem(val target: RenderTarget<*>, val name: String, val textureId: Int) {
    override fun toString() = name
}