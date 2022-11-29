package de.hanno.hpengine.model

import EntityStruktImpl.Companion.type
import de.hanno.hpengine.graphics.EntityStrukt
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.typed

class EntityBuffer(
    val underlying: TypedBuffer<EntityStrukt> = BufferUtils.createByteBuffer(EntityStrukt.type.sizeInBytes).typed(
        EntityStrukt.type
    )
)
