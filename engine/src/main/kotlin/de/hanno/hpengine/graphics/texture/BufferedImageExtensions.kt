package de.hanno.hpengine.graphics.texture

import java.awt.color.ColorSpace
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer


/** The colour model including alpha for the GL image  */
val glAlphaColorModel = ComponentColorModel(
    ColorSpace.getInstance(ColorSpace.CS_sRGB),
    intArrayOf(8, 8, 8, 8),
    true,
    false,
    ComponentColorModel.TRANSLUCENT,
    DataBuffer.TYPE_BYTE
)

/** The colour model for the GL image  */
val glColorModel = ComponentColorModel(
    ColorSpace.getInstance(ColorSpace.CS_sRGB),
    intArrayOf(8, 8, 8, 0),
    false,
    false,
    ComponentColorModel.OPAQUE,
    DataBuffer.TYPE_BYTE
)