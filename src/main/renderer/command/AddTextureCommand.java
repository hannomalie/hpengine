package main.renderer.command;

import java.io.IOException;

import main.World;
import main.renderer.Result;
import main.renderer.command.AddTextureCommand.TextureResult;
import main.texture.Texture;

public class AddTextureCommand implements Command<TextureResult> {

	String path;
	
	public AddTextureCommand(String path) {
		this.path = path;
	}
	
	@Override
	public TextureResult execute(World world) {
		Texture texture = null;
		try {
			texture = world.getRenderer().getTextureFactory().getTexture(path);
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
