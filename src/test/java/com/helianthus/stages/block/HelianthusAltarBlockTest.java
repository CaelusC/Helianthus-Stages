package com.helianthus.stages.block;

import com.helianthus.stages.block.entity.HelianthusAltarBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelianthusAltarBlockTest
{
    private static HelianthusAltarBlock block;
    private static BlockEntityType<HelianthusAltarBlockEntity> altarType;

    @BeforeAll
    static void setUp()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Blocks and block entity types take an intrusive registry holder on construction
        GameData.unfreezeData();
        block = new HelianthusAltarBlock(BlockBehaviour.Properties.of());
        altarType = BlockEntityType.Builder.<HelianthusAltarBlockEntity>of((pos, state) -> null, block).build(null);
    }

    @Test
    void isEntityBlockRenderedAsModel()
    {
        assertInstanceOf(BaseEntityBlock.class, block);
        assertInstanceOf(EntityBlock.class, block);
        assertEquals(RenderShape.MODEL, block.getRenderShape(block.defaultBlockState()));
        assertTrue(altarType.isValid(block.defaultBlockState()));
    }

    @Test
    void tickerBoundOnServerOnly()
    {
        assertNotNull(HelianthusAltarBlock.serverTicker(false, altarType, altarType));
        assertNull(HelianthusAltarBlock.serverTicker(true, altarType, altarType));
    }

    @Test
    void noTickerForOtherBlockEntityTypes()
    {
        assertNull(HelianthusAltarBlock.serverTicker(false, BlockEntityType.CHEST, altarType));
    }
}
