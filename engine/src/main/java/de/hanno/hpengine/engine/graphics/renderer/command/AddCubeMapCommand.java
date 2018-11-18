package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.model.texture.CubeMap;
import de.hanno.hpengine.engine.model.texture.TextureManager;

import java.io.IOException;

public class AddCubeMapCommand extends AddTextureCommand {

	public AddCubeMapCommand(String path, TextureManager textureManager) {
		super(path, textureManager);
	}

	@Override
	public TextureResult execute() {
		CubeMap texture = null;
		try {
            texture = textureManager.getCubeMap(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
}
