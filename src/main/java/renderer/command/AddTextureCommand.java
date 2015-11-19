package renderer.command;

import engine.AppContext;
import renderer.command.AddTextureCommand.TextureResult;
import texture.Texture;
import texture.TextureFactory;

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
	public TextureResult execute(AppContext appContext) {
		Texture texture = null;
		try {
			texture = TextureFactory.getInstance().getTexture(path, srgba);
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
