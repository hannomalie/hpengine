package renderer.command;

import engine.Engine;
import texture.Texture;
import texture.TextureFactory;

import java.io.IOException;

public class AddCubeMapCommand extends AddTextureCommand {

	public AddCubeMapCommand(String path) {
		super(path);
	}

	@Override
	public TextureResult execute(Engine engine) {
		Texture texture = null;
		try {
			texture = TextureFactory.getInstance().getCubeMap(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
}
