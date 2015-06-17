package renderer.command;

import java.io.IOException;

import engine.World;
import texture.Texture;

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
