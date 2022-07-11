package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.model.Mesh

data class BatchKey(val mesh: Mesh<*>, val entityIndex: Int, val clusterIndex : Int)