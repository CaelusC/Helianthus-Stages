package com.helianthus.stages.block;

import com.helianthus.stages.block.entity.HelianthusAltarBlockEntity;
import com.helianthus.stages.registry.HelianthusRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

/**
 * Physical altar. Hosts a {@link HelianthusAltarBlockEntity} and ticks it on the server only.
 */
public class HelianthusAltarBlock extends BaseEntityBlock
{
    public HelianthusAltarBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HelianthusAltarBlockEntity(HelianthusRegistry.ALTAR_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return serverTicker(level.isClientSide, type, HelianthusRegistry.ALTAR_BLOCK_ENTITY.get());
    }

    @Nullable
    static <T extends BlockEntity> BlockEntityTicker<T> serverTicker(boolean clientSide, BlockEntityType<T> type,
            BlockEntityType<HelianthusAltarBlockEntity> expected)
    {
        return clientSide ? null : createTickerHelper(type, expected, HelianthusAltarBlockEntity::serverTick);
    }

    // BaseEntityBlock defaults to INVISIBLE
    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }
}
