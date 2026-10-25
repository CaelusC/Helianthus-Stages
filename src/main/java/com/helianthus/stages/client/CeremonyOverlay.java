package com.helianthus.stages.client;

import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.network.ClientSyncCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Top-centre HUD showing the nearest active altar ceremony's stage, progress and house readiness.
 */
public final class CeremonyOverlay implements IGuiOverlay
{
    public static final String ID = "ceremony";

    private static final int TOP = 8;
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BAR_BACKGROUND = 0xFF404040;
    private static final int BAR_FILL = 0xFFFFC020;
    private static final int READY_COLOR = 0x55FF55;
    private static final int WAITING_COLOR = 0xAAAAAA;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui)
            return;

        CeremonyHudData.nearestActive(ClientSyncCache.getCeremonies().values(),
                        player.getX(), player.getY(), player.getZ(), ConfigManager.getGate().radius())
                .map(ceremony -> CeremonyHudData.from(ceremony, ConfigManager.getStages(), ConfigManager.getHouses()))
                .ifPresent(data -> draw(graphics, minecraft.font, screenWidth, data));
    }

    private static void draw(GuiGraphics graphics, Font font, int screenWidth, CeremonyHudData data)
    {
        int centre = screenWidth / 2;
        graphics.drawCenteredString(font, data.title(), centre, TOP, TEXT_COLOR);

        int barLeft = centre - BAR_WIDTH / 2;
        int barTop = TOP + font.lineHeight + 2;
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, BAR_BACKGROUND);
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH * data.progressPercent() / 100, barTop + BAR_HEIGHT, BAR_FILL);

        int x = centre - font.width(data.readinessLine()) / 2;
        int y = barTop + BAR_HEIGHT + 3;
        for (CeremonyHudData.HouseStatus house : data.houses())
            x = graphics.drawString(font, house.label() + " ", x, y, house.ready() ? READY_COLOR : WAITING_COLOR);
    }
}
