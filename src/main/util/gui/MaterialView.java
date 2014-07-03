package main.util.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector3f;

import main.World;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.texture.Texture;
import main.util.gui.input.ColorChooserButton;
import main.util.gui.input.ColorChooserFrame;
import main.util.gui.input.WebFormattedVec3Field;

import com.alee.extended.panel.BorderPanel;
import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebTextField;

public class MaterialView extends WebPanel {

	private Material material;
	private World world;

	public MaterialView(World world, Material material) {
		this.material = material;
		this.world = world;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<WebComponentPanel> panels = new ArrayList<>();
		
		addTexturePanel(panels);
        addValuePanels(panels);
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        this.add(new GridPanel ( panels.size(), 1, components));
	}

	private void addTexturePanel(List<WebComponentPanel> panels) {
		
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        for (MAP map : material.textures.textures.keySet()) {
			Texture texture = material.textures.textures.get(map);
			
	        WebLabel label = new WebLabel ( map.name() );
	        
	        Texture[] textures = new Texture[world.getRenderer().getTextureFactory().TEXTURES.values().size()];
	        world.getRenderer().getTextureFactory().TEXTURES.values().toArray(textures);
	        WebComboBox select = new WebComboBox(textures);
	        
	        List allTexturesList = new ArrayList(world.getRenderer().getTextureFactory().TEXTURES.values());
	        int assignedTexture = allTexturesList.indexOf(world.getRenderer().getTextureFactory().TEXTURES.get(material.textures.textures.get(map).getPath()));
	        select.setSelectedIndex(assignedTexture);
	        
	        select.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Texture selectedTexture = textures[cb.getSelectedIndex()];
	        	material.textures.put(map, selectedTexture);
	        });
	        
	        GroupPanel groupPanel = new GroupPanel ( 4, label, select );
			webComponentPanel.addElement(groupPanel);
	        
		}
        
        panels.add(webComponentPanel);
	}

	private void addValuePanels(List<WebComponentPanel> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        webComponentPanel.addElement(new WebFormattedVec3Field("Diffuse", material.getDiffuse()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setDiffuse(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Diffuse", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setDiffuse(color);
			}
		}));

        webComponentPanel.addElement(new WebFormattedVec3Field("Ambient", material.getAmbient()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setAmbient(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Ambient", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setAmbient(color);
			}
		}));
        
        webComponentPanel.addElement(new WebFormattedVec3Field("SpecularColor", material.getSpecular()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setSpecular(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("SpecularColor", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setSpecular(color);
			}
		}));
		
		//TODO: add other attributes here
		
		panels.add(webComponentPanel);
	}
}
