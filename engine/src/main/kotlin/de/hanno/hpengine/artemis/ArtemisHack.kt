package com.artemis

val <T : Component> ComponentMapper<T>.hackedOutComponents get() = components.data.filterNotNull()
val <T : Component> ComponentMapper<T>.hackedOutSize get() = components.size()