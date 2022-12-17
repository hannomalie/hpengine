package de.hanno.hpengine.graphics.renderer.constants

import PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import org.lwjgl.opengl.GL30


internal val RenderingMode.glValue: Int get() = when(this) {
    RenderingMode.Lines -> GL30.GL_LINES
    RenderingMode.Fill -> GL30.GL_FILL
}

internal val PrimitiveType.glValue: Int get() = when(this) {
    PrimitiveType.Lines -> GL30.GL_LINES
    PrimitiveType.Triangles -> GL30.GL_FILL
    PrimitiveType.Patches -> GL30.GL_FILL
}