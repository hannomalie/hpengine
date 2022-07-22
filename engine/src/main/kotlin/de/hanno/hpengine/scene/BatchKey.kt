package de.hanno.hpengine.scene

import de.hanno.hpengine.model.Mesh

data class BatchKey(val mesh: Mesh<*>, val entityIndex: Int, val clusterIndex : Int)