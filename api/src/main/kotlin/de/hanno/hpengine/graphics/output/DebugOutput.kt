package de.hanno.hpengine.graphics.output

import de.hanno.hpengine.graphics.texture.Texture2D

data class DebugOutput(var texture2D: Texture2D? = null, var mipmapLevel: Int = 0)