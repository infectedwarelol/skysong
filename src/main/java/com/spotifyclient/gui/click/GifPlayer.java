package com.spotifyclient.gui.click;

import com.mojang.blaze3d.platform.NativeImage;
import com.spotifyclient.SpotifyModClient;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Gpu;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.compat.Ids;
import com.spotifyclient.render.r2d.R2D;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class GifPlayer {
    private final Identifier source;
    private final List<Integer> textures = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private boolean loaded;
    private boolean failed;
    private int totalMs;

    public GifPlayer(Identifier source) {
        this.source = source;
    }

    public void draw(GuiGraphics c, float x, float y, float w, float h, float radius, float alpha) {
        if (!this.loaded && !this.failed) {
            this.load();
        }

        if (!this.textures.isEmpty()) {
            long t = System.currentTimeMillis() % Math.max(1, this.totalMs);
            int frame = 0;
            int acc = 0;

            while (frame < this.delays.size()) {
                acc += this.delays.get(frame);
                if (t < acc) {
                    break;
                }

                frame++;
            }

            frame = Math.min(frame, this.textures.size() - 1);
            R2D.image(c, this.textures.get(frame), x, y, w, h, radius, 0.0F, 0.0F, 1.0F, 1.0F, Math.round(alpha * 255.0F) << 24 | 16777215);
        }
    }

    private void load() {
        this.loaded = true;

        try (
            InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(this.source).open();
            ImageInputStream stream = ImageIO.createImageInputStream(in);
        ) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            ImageReader reader = readers.next();
            reader.setInput(stream);
            int count = reader.getNumImages(true);
            BufferedImage canvas = null;

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                int left = 0;
                int top = 0;
                int delay = 100;
                String disposal = "none";

                for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    NamedNodeMap attrs = n.getAttributes();
                    if (n.getNodeName().equals("ImageDescriptor")) {
                        left = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                        top = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                    } else if (n.getNodeName().equals("GraphicControlExtension")) {
                        delay = Math.max(20, Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue()) * 10);
                        disposal = attrs.getNamedItem("disposalMethod").getNodeValue();
                    }
                }

                if (canvas == null) {
                    int w = Math.max(frame.getWidth() + left, reader.getWidth(0));
                    int h = Math.max(frame.getHeight() + top, reader.getHeight(0));
                    canvas = new BufferedImage(w, h, 2);
                }

                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();
                this.textures.add(this.upload(canvas, i));
                this.delays.add(delay);
                this.totalMs += delay;
                if (disposal.equals("restoreToBackgroundColor")) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
                    clear.dispose();
                }
            }

            reader.dispose();
        } catch (Exception e) {
            this.failed = true;
            SpotifyModClient.LOGGER.warn("[Versefall] Could not load " + this.source + ": " + e);
        }
    }

    private int upload(BufferedImage img, int index) {
        NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), false);

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int abgr = argb & -16711936 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
                Compat.setPixelAbgr(ni, x, y, abgr);
            }
        }

        DynamicTexture tex = Compat.dynamicTexture("gif", ni);
        Identifier id = Ids.of("spotify_mod", "gif_frame_" + this.source.getPath().replace('/', '_') + "_" + index);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return Gpu.textureId(tex, true);
    }
}
