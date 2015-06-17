package renderer.command;

import engine.World;
import renderer.Result;
import renderer.command.AddTextureCommand.TextureResult;
import texture.Texture;

public class AddTextureCommand implements Command<TextureResult> {

	String path;
	boolean srgba = false;

	public AddTextureCommand(String path) {
		this(path, false);
	}
	
	public AddTextureCommand(String path, boolean srba) {
		this.path = path;
		this.srgba = srba;
	}
	
	@Override
	public TextureResult execute(World world) {
		Texture texture = null;
		try {
			texture = world.getRenderer().getTextureFactory().getTexture(path, srgba);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
	
	public static class TextureResult extends Result {
		Texture texture;
		private boolean successful = false;
		
		public TextureResult(Texture texture) {
			this.texture = texture;
			if(texture != null) {
				successful = true;
			}
		}

		@Override
		public boolean isSuccessful() {
			return successful;
		}
	}

}
