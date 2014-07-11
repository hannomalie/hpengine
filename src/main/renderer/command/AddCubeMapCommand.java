package main.renderer.command;

import java.io.IOException;

import main.World;
import main.texture.Texture;

public class AddCubeMapCommand extends AddTextureCommand {

	public AddCubeMapCommand(String path) {
		super(path);
	}

	@Override
	public TextureResult execute(World world) {
		Texture texture = null;
		try {
			texture = world.getRenderer().getTextureFactory().getCubeMap(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
}
