// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

import javax.swing.ImageIcon;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.styleelement.BoxTextElement.BoxProvider;
import org.openstreetmap.josm.gui.mappaint.styleelement.BoxTextElement.BoxProviderResult;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Utils;

/**
 * An image that will be displayed on the map.
 */
public class MapImage {

    private static final int MAX_SIZE = 48;

    /**
     * ImageIcon can change while the image is loading.
     */
    private BufferedImage img;

    public int alpha = 255;
    public String name;
    public StyleSource source;
    public boolean autoRescale;
    public int width = -1;
    public int height = -1;
    public int offsetX;
    public int offsetY;

    private boolean temporary;
    private BufferedImage disabledImgCache;

    public MapImage(String name, StyleSource source) {
        this(name, source, true);
    }

    public MapImage(String name, StyleSource source, boolean autoRescale) {
        this.name = name;
        this.source = source;
        this.autoRescale = autoRescale;
    }

    /**
     * Get the image associated with this MapImage object.
     *
     * @param disabled {@code} true to request disabled version, {@code false} for the standard version
     * @return the image
     */
    public BufferedImage getImage(boolean disabled) {
        if (disabled) {
            return getDisabled();
        } else {
            return getImage();
        }
    }

    private BufferedImage getDisabled() {
        if (disabledImgCache != null)
                return disabledImgCache;
        if (img == null)
            getImage(); // fix #7498 ?
        Image disImg = GuiHelper.getDisabledImage(img);
        if (disImg instanceof BufferedImage) {
            disabledImgCache = (BufferedImage) disImg;
        } else {
            disabledImgCache = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = disabledImgCache.getGraphics();
            g.drawImage(disImg, 0, 0, null);
            g.dispose();
        }
        return disabledImgCache;
    }

    private BufferedImage getImage() {
        if (img != null)
            return img;
        temporary = false;
        new ImageProvider(name)
                .setDirs(MapPaintStyles.getIconSourceDirs(source))
                .setId("mappaint."+source.getPrefName())
                .setArchive(source.zipIcons)
                .setInArchiveDir(source.getZipEntryDirName())
                .setWidth(width)
                .setHeight(height)
                .setOptional(true)
                .getAsync().thenAccept(result -> {
                    synchronized (this) {
                        if (result == null) {
                            source.logWarning(tr("Failed to locate image ''{0}''", name));
                            ImageIcon noIcon = MapPaintStyles.getNoIconIcon(source);
                            img = noIcon == null ? null : (BufferedImage) noIcon.getImage();
                        } else {
                            img = (BufferedImage) rescale(result.getImage());
                        }
                        if (temporary) {
                            disabledImgCache = null;
                            Main.map.mapView.preferenceChanged(null); // otherwise repaint is ignored, because layer hasn't changed
                            Main.map.mapView.repaint();
                        }
                        temporary = false;
                    }
                }
        );
        synchronized (this) {
            if (img == null) {
                img = (BufferedImage) ImageProvider.get("clock").getImage();
                temporary = true;
            }
        }
        return img;
    }

    public int getWidth() {
        return getImage().getWidth(null);
    }

    public int getHeight() {
        return getImage().getHeight(null);
    }

    public float getAlphaFloat() {
        return Utils.colorInt2float(alpha);
    }

    /**
     * Determines if image is not completely loaded and {@code getImage()} returns a temporary image.
     * @return {@code true} if image is not completely loaded and getImage() returns a temporary image
     */
    public boolean isTemporary() {
        return temporary;
    }

    protected class MapImageBoxProvider implements BoxProvider {
        @Override
        public BoxProviderResult get() {
            return new BoxProviderResult(box(), temporary);
        }

        private Rectangle box() {
            int w = getWidth(), h = getHeight();
            if (mustRescale(getImage())) {
                w = 16;
                h = 16;
            }
            return new Rectangle(-w/2, -h/2, w, h);
        }

        private MapImage getParent() {
            return MapImage.this;
        }

        @Override
        public int hashCode() {
            return MapImage.this.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BoxProvider))
                return false;
            if (obj instanceof MapImageBoxProvider) {
                MapImageBoxProvider other = (MapImageBoxProvider) obj;
                return MapImage.this.equals(other.getParent());
            } else if (temporary) {
                return false;
            } else {
                final BoxProvider other = (BoxProvider) obj;
                BoxProviderResult resultOther = other.get();
                if (resultOther.isTemporary()) return false;
                return box().equals(resultOther.getBox());
            }
        }
    }

    public BoxProvider getBoxProvider() {
        return new MapImageBoxProvider();
    }

    /**
     * Rescale excessively large images.
     * @param image the unscaled image
     * @return The scaled down version to 16x16 pixels if the image height and width exceeds 48 pixels and no size has been explicitely specified
     */
    private Image rescale(Image image) {
        if (image == null) return null;
        // Scale down large (.svg) images to 16x16 pixels if no size is explicitely specified
        if (mustRescale(image)) {
            return ImageProvider.createBoundedImage(image, 16);
        } else {
            return image;
        }
    }

    private boolean mustRescale(Image image) {
        return autoRescale && width == -1 && image.getWidth(null) > MAX_SIZE
             && height == -1 && image.getHeight(null) > MAX_SIZE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MapImage mapImage = (MapImage) obj;
        return alpha == mapImage.alpha &&
                autoRescale == mapImage.autoRescale &&
                width == mapImage.width &&
                height == mapImage.height &&
                Objects.equals(name, mapImage.name) &&
                Objects.equals(source, mapImage.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alpha, name, source, autoRescale, width, height);
    }

    @Override
    public String toString() {
        return name;
    }
}
