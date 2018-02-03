package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.material.MaterialFactory
import java.io.File
import java.io.IOException

class ModelFactory {
    companion object {
        val sphereModel by lazy {
            val sphereModel : StaticModel<Bufferable> = try {
                val sphereModel = OBJLoader().loadTexturedModel(File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"))
                sphereModel.setMaterial(Engine.getInstance().materialFactory.defaultMaterial)
                sphereModel
            } catch (e: IOException) {
                e.printStackTrace()
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }
}