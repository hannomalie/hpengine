package de.hanno.hpengine.graphics

import java.util.*


val DEFAULTCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.TEXCOORD,
    DataChannels.NORMAL
)
val DEFAULTANIMATEDCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.TEXCOORD,
    DataChannels.NORMAL,
    DataChannels.WEIGHTS,
    DataChannels.JOINT_INDICES
)
val DEPTHCHANNELS = EnumSet.of(
    DataChannels.POSITION3,
    DataChannels.NORMAL
)
val SHADOWCHANNELS = EnumSet.of(
    DataChannels.POSITION3
)
val POSITIONCHANNEL = EnumSet.of(
    DataChannels.POSITION3
)