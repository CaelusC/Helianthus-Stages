package com.helianthus.stages.client;

import com.helianthus.stages.HelianthusStages;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only mod bus wiring; the Dist filter keeps these classes off dedicated servers.
 */
@Mod.EventBusSubscriber(modid = HelianthusStages.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup
{
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event)
    {
        event.registerAboveAll(CeremonyOverlay.ID, new CeremonyOverlay());
    }
}
