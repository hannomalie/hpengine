package de.hanno.hpengine.graphics.output

import de.hanno.hpengine.graphics.texture.Texture2D

data class FinalOutput(var texture2D: Texture2D, var mipmapLevel: Int = 0)