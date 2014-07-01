package main.util.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import main.World;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.texture.Texture;

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
		
        for (MAP map : material.textures.keySet()) {
			Texture texture = material.textures.get(map);
			panels.add(getComponentPanel(map, texture));
		}
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        this.add(new GridPanel ( panels.size(), 1, components));
	}

	private WebComponentPanel getComponentPanel(MAP map, Texture texture) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        WebLabel label = new WebLabel ( map.name() );
//        WebTextField field = new WebTextField ();
//        field.putClientProperty ( GroupPanel.FILL_CELL, true );
//        field.setText(texture.toString());
        
        Texture[] textures = new Texture[world.getRenderer().getTextureFactory().TEXTURES.values().size()];
        world.getRenderer().getTextureFactory().TEXTURES.values().toArray(textures);
        WebComboBox select = new WebComboBox(textures);
        
        int assignedTexture = ( new ArrayList(world.getRenderer().getTextureFactory().TEXTURES.values())).indexOf(material.textures.get(map));
        select.setSelectedIndex(assignedTexture);
        
        select.addActionListener(e -> {
        	WebComboBox cb = (WebComboBox) e.getSource();
        	Texture selectedTexture = textures[cb.getSelectedIndex()];
        	material.textures.put(map, selectedTexture);
        });
        
		webComponentPanel.addElement ( new GroupPanel ( 10, label, select ) );
        return webComponentPanel;
	}

}
