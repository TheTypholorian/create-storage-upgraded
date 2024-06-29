package net.fxnt.fxntstorage.passer;

import com.simibubi.create.AllTags;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fxnt.fxntstorage.cache.PasserShapeCache;
import net.fxnt.fxntstorage.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PasserBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public final boolean isSmart;

    public PasserBlock(FabricBlockSettings properties, boolean isSmart) {
        super(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).hardness(1.5f));

        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.DOWN));
        this.isSmart = isSmart;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (this.isSmart) {
            return new PasserSmartEntity(pos, state);
        } else {
            return new PasserEntity(pos, state);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    private HitPart hitPart(Direction direction, BlockHitResult hit) {
        if (hit.getDirection() == direction || hit.getDirection() == direction.getOpposite()) {
            return HitPart.A;
        } else if (hit.getDirection() == Direction.UP || hit.getDirection() == Direction.DOWN) {
            return HitPart.B;
        } else {
            return HitPart.C;
        }
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (hand == InteractionHand.OFF_HAND) {
                return InteractionResult.SUCCESS;
            }

            if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(AllTags.AllItemTags.WRENCH.tag)) {
                return InteractionResult.PASS;
            }

            Direction direction = blockState.getValue(FACING);

            switch (hitPart(direction, hit)) {
                case B:
                    // Hit top / bottom rotate horizontally
                    direction = switch (blockState.getValue(FACING)) {
                        case NORTH -> Direction.EAST;
                        case EAST -> Direction.SOUTH;
                        case SOUTH -> Direction.WEST;
                        case WEST -> Direction.NORTH;
                        case UP, DOWN -> direction;
                    };
                    break;
                case C:
                    if (hit.getDirection() == Direction.EAST || hit.getDirection() == Direction.WEST) {
                        direction = switch (direction) {
                            case UP -> Direction.SOUTH;
                            case DOWN -> Direction.NORTH;
                            case NORTH -> Direction.UP;
                            case SOUTH -> Direction.DOWN;
                            case EAST, WEST -> direction;
                        };
                    } else if (hit.getDirection() == Direction.NORTH || hit.getDirection() == Direction.SOUTH) {
                        direction = switch (direction) {
                            case UP -> Direction.WEST;
                            case EAST -> Direction.UP;
                            case DOWN -> Direction.EAST;
                            case WEST -> Direction.DOWN;
                            case NORTH, SOUTH -> direction;
                        };
                    }
                    break;
            }

            level.setBlockAndUpdate(blockPos, blockState.setValue(FACING, direction));
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (this.isSmart) {
            return createTickerHelper(blockEntityType, ModBlocks.SMART_PASSER_ENTITY, (type, world, pos, entity) -> {
                entity.serverTick(type, world, entity);
            });
        } else {
            return createTickerHelper(blockEntityType, ModBlocks.PASSER_ENTITY, (type, world, pos, entity) -> {
                entity.serverTick(type, world, entity);
            });
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PasserShapeCache.getShape(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PasserShapeCache.getShape(state.getValue(FACING));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return PasserShapeCache.getShape(state.getValue(FACING));
    }

    private static enum HitPart {
        A, B, C
    }
}
