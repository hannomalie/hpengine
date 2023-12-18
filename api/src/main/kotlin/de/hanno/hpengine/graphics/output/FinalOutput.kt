package de.hanno.hpengine.graphics.output

import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.texture.Texture2D

interface FinalOutput {
    var texture2D: Texture2D
    var mipmapLevel: Int
    val producedBy: RenderSystem?
}
fun FinalOutput(texture2D: Texture2D, mipmapLevel: Int = 0, producedBy: RenderSystem? = null): FinalOutput = FinalOutputImpl(
    texture2D,
    mipmapLevel,
    producedBy,
)
data class FinalOutputImpl(override var texture2D: Texture2D, override var mipmapLevel: Int = 0, override val producedBy: RenderSystem? = null): FinalOutput