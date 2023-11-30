package de.hanno.hpengine.artemis

import com.artemis.BaseEntitySystem
import com.artemis.utils.IntBag

fun <T> BaseEntitySystem.mapEntity(block: (Int) -> T): MutableList<T> {
    val result = mutableListOf<T>()
    val actives: IntBag = subscription.entities
    val ids = actives.data
    var i = 0
    val s = actives.size()
    while (s > i) {
        result.add(block(ids[i]))
        i++
    }
    return result
}

inline fun <T> BaseEntitySystem.forEachEntity(block: (Int) -> T) {
    subscription.entities.forEach(block)
}

inline fun <T> BaseEntitySystem.forFirstEntityIfPresent(block: (Int) -> T): T? {
    val ids = subscription.entities.data
    val s = subscription.entities.size()
    return if(s > 0) {
        block(ids[0])
    } else null
}

inline fun <T> IntBag.forEach(block: (Int) -> T) {
    val ids = data
    var i = 0
    val s = size()
    while (s > i) {
        block(ids[i])
        i++
    }
}