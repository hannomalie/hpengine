package de.hanno.hpengine.graphics.texture

import java.awt.color.ColorSpace
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer


val alphaColorModel = ComponentColorModel(
    ColorSpace.getInstance(ColorSpace.CS_sRGB),
    intArrayOf(8, 8, 8, 8),
    true,
    false,
    ComponentColorModel.TRANSLUCENT,
    DataBuffer.TYPE_BYTE
)

val colorModel = ComponentColorModel(
    ColorSpace.getInstance(ColorSpace.CS_sRGB),
    intArrayOf(8, 8, 8, 0),
    false,
    false,
    ComponentColorModel.OPAQUE,
    DataBuffer.TYPE_BYTE
)