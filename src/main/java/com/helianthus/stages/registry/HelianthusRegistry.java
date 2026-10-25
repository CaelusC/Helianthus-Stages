package com.helianthus.stages.registry;

import com.helianthus.stages.HelianthusStages;
import com.helianthus.stages.block.HelianthusAltarBlock;
import com.helianthus.stages.block.entity.HelianthusAltarBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HelianthusRegistry
{
    public static final String ALTAR_ID = "helianthus_altar";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HelianthusStages.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HelianthusStages.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HelianthusStages.MODID);

    public static final RegistryObject<HelianthusAltarBlock> ALTAR_BLOCK = BLOCKS.register(ALTAR_ID,
            () -> new HelianthusAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.5f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> ALTAR_ITEM = ITEMS.register(ALTAR_ID,
            () -> new BlockItem(ALTAR_BLOCK.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue") // null data fixer type is the standard for modded block entities
    public static final RegistryObject<BlockEntityType<HelianthusAltarBlockEntity>> ALTAR_BLOCK_ENTITY = BLOCK_ENTITIES.register(ALTAR_ID,
            () -> BlockEntityType.Builder.<HelianthusAltarBlockEntity>of(
                    (pos, state) -> new HelianthusAltarBlockEntity(HelianthusRegistry.ALTAR_BLOCK_ENTITY.get(), pos, state), ALTAR_BLOCK.get()).build(null));

    private HelianthusRegistry() {}

    public static void register(IEventBus modEventBus)
    {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
