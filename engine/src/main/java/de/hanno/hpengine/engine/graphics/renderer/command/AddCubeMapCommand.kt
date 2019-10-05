package de.hanno.hpengine.engine.graphics.renderer.command

import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureManager

import java.io.IOException

class AddCubeMapCommand(path: String, textureManager: TextureManager, gameDir: GameDirectory) : AddTextureCommand(path, textureManager, gameDir) {

    override fun execute(): AddTextureCommand.TextureResult {
        var texture: CubeMap? = null
        try {
            texture = textureManager.getCubeMap(path, gameDir.resolve(path))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return AddTextureCommand.TextureResult(texture)
    }
}
