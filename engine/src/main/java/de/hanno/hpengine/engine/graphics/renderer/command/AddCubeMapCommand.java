package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.texture.CubeMap;

import java.io.IOException;

public class AddCubeMapCommand extends AddTextureCommand {

	public AddCubeMapCommand(String path) {
		super(path);
	}

	@Override
	public TextureResult execute(Engine engine) {
		CubeMap texture = null;
		try {
            texture = engine.getTextureManager().getCubeMap(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
}
