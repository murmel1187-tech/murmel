package de.murmelsvisuals.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Base screen that draws our own translucent panels instead of the blurred vanilla background. */
public abstract class PanelScreen extends Screen {
    protected final List<Panel> panels = new ArrayList<>();
    /** 0 = clear, 1 = light, 2 = dark */
    protected int dim = 1;

    protected PanelScreen(Component title) {
        super(title);
    }

    // Deliberately no super call: no blur and no vanilla dimming, so the game stays visible behind the menu.
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PanelPainter.paintAll(panels, mouseX, mouseY, dim, this.width, this.height,
                (x1, y1, x2, y2, argb) -> graphics.fill(x1, y1, x2, y2, argb));
    }
}
