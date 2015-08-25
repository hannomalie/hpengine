package renderer.command;

import java.io.IOException;

import engine.AppContext;
import texture.Texture;

public class AddCubeMapCommand extends AddTextureCommand {

	public AddCubeMapCommand(String path) {
		super(path);
	}

	@Override
	public TextureResult execute(AppContext appContext) {
		Texture texture = null;
		try {
			texture = appContext.getRenderer().getTextureFactory().getCubeMap(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
}
