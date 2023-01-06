package de.hanno.hpengine.artemis

import com.artemis.Component
import com.artemis.ComponentMapper

fun <T: Component> ComponentMapper<T>.getOrNull(entityId: Int): T? = if (has(entityId)) get(entityId) else null