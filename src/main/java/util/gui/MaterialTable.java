package util.gui;

import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import renderer.material.Material;
import renderer.material.MaterialFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class MaterialTable extends JTable {

    private static AbstractTableModel tableModel = getTableModel();

    private WebFrame materialViewFrame = new WebFrame("Material");

    public MaterialTable() {
        super(getTableModel());
        MaterialTable self = this;

        getSelectionModel().addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event) {
                materialViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                materialViewFrame.getContentPane().removeAll();
                materialViewFrame.pack();
                materialViewFrame.setSize(600, 600);
                WebScrollPane scrollPane = new WebScrollPane(new MaterialView((Material) tableModel.getValueAt(self.getSelectedRow(), 1)));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                materialViewFrame.add(scrollPane);
                materialViewFrame.setVisible(true);
            }
        });
    }

    private static AbstractTableModel getTableModel() {
        AbstractTableModel tableModel = new AbstractTableModel() {

            public int getColumnCount() {
                return 2;
            }

            public int getRowCount() {
                try {
                    return MaterialFactory.getInstance().MATERIALS.size();
                } catch (IllegalStateException e) {
                    return 0;
                }
            }

            public Object getValueAt(int row, int col) {

                List<Object> paths = Arrays.asList(MaterialFactory.getInstance().MATERIALS.keySet().toArray());

                if (col == 0) {
                    return paths.get(row);
                }
                List<Object> materials = Arrays.asList(MaterialFactory.getInstance().MATERIALS.values().toArray());
                return materials.get(row);
            }

            public String getColumnName(int column) {
                if (column == 0) {
                    return "Path";
                } else if (column == 1) {
                    return "Material";
                }
                return "Null";
            }
        };
        return tableModel;
    }
}
