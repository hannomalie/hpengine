package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.struct.Struct
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT

class DrawElementsIndirectCommand : Struct() {
    var count by 0
    var primCount by 0
    var firstIndex by 0
    var baseVertex by 0
    var baseInstance by 0
}


