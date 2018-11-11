package de.hanno.hpengine.engine.event

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.Mesh

class MeshSelectedEvent(val entity: Entity, val mesh: Mesh<*>)