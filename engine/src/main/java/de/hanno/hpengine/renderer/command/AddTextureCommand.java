package de.hanno.hpengine.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.texture.Texture;
import de.hanno.hpengine.texture.TextureFactory;

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
	public TextureResult execute(Engine engine) {
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
