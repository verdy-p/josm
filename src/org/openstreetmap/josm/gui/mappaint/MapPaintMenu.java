// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.MapPaintDialog;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.MapPaintStylesUpdateListener;
import org.openstreetmap.josm.gui.util.StayOpenCheckBoxMenuItem;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * The View → Map Paint Styles menu
 * @since 5086
 */
public class MapPaintMenu extends JMenu implements MapPaintStylesUpdateListener {

    private static class MapPaintAction extends JosmAction {

        private transient StyleSource style;
        private final JCheckBoxMenuItem button;

        MapPaintAction(StyleSource style) {
            super(style.getDisplayString(), style.getIconProvider(),
                    tr("Select the map painting styles"), null, true, "mappaint/" + style.getDisplayString(), true);
            this.button = new StayOpenCheckBoxMenuItem(this);
            this.style = style;
            updateButton();
            putValue("help", ht("/Dialog/MapPaint"));
        }

        private void updateButton() {
            button.getModel().setSelected(style.active);
        }

        private void toggleStyle() {
            int index = MapPaintStyles.getStyles().getStyleSources().indexOf(style);
            if (index >= 0) {
                MapPaintStyles.toggleStyleActive(index);
            }
            updateButton();
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            toggleStyle();
        }

        public JCheckBoxMenuItem getButton() {
            return button;
        }

        public void setStyle(StyleSource style) {
            this.style = style;
        }

        @Override
        protected boolean listenToSelectionChange() {
            return false;
        }

        @Override
        public void updateEnabledState() {
            setEnabled(MainApplication.isDisplayingMapView()
                    && (MainApplication.getLayerManager().getActiveData() != null || mapHasGpxOrMarkerLayer()));
        }

        private static boolean mapHasGpxOrMarkerLayer() {
            return MainApplication.getLayerManager().getLayers().stream()
                    .anyMatch(layer -> layer instanceof GpxLayer || layer instanceof MarkerLayer);
        }
    }

    private final transient Map<String, MapPaintAction> actions = new HashMap<>();

    /**
     * Constructs a new {@code MapPaintMenu}
     */
    public MapPaintMenu() {
        super(tr("Map Paint Styles"));
        setIcon(ImageProvider.get("dialogs", "mapstyle", ImageProvider.ImageSizes.MENU));
        MapPaintStyles.addMapPaintStylesUpdateListener(this);
        putClientProperty("help", ht("/Dialog/MapPaint"));
    }

    @Override
    public void mapPaintStylesUpdated() {
        removeAll();
        for (StyleSource style : MapPaintStyles.getStyles().getStyleSources()) {
            final String k = style.getDisplayString();
            MapPaintAction a = actions.get(k);
            if (a == null) {
                a = new MapPaintAction(style);
                actions.put(k, a);
                add(a.getButton());
            } else {
                a.setStyle(style);
                add(a.getButton());
                a.updateButton();
            }
        }
        addSeparator();
        add(MapPaintDialog.PREFERENCE_ACTION);
    }

    @Override
    public void mapPaintStyleEntryUpdated(int idx) {
        mapPaintStylesUpdated();
    }
}
