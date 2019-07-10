package ladysnake.illuminations.common.entities;

import ladysnake.illuminations.common.blocks.FairyBellBlock;
import ladysnake.illuminations.common.init.IlluminationsBlocks;
import ladysnake.illuminations.common.init.IlluminationsEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnType;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FairyEntity extends LightOrbEntity {
    // Attributes
    private static final TrackedData<Integer> COLOR;

    // Constructors
    public FairyEntity(EntityType entityType, World world) {
        super(entityType, world);

        this.setColor(randomDualTone());
        this.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(10.0D);
    }

    public FairyEntity(World world, double x, double y, double z) {
        this(IlluminationsEntities.FAIRY, world);
        this.setPosition(x, y, z);
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(COLOR, 0);
    }

    // Getters & setters
    public int getColor() {
        return this.dataTracker.get(COLOR);
    }

    public void setColor(int color) {
        this.dataTracker.set(COLOR, color);
    }


    // NBT
    @Override
    public void writeCustomDataToTag(CompoundTag compoundTag) {
        super.writeCustomDataToTag(compoundTag);
        compoundTag.putInt("color", this.getColor());
    }

    @Override
    public void readCustomDataFromTag(CompoundTag compoundTag) {
        super.readCustomDataFromTag(compoundTag);
        if (compoundTag.getInt("color") != 0) {
            this.setColor(compoundTag.getInt("color"));
        }
    }

    // Properties
    @Override
    public boolean hasNoGravity() {
        return true;
    }

    // Behaviour
    private double groundLevel;
    private double xTarget;
    private double yTarget;
    private double zTarget;
    private int targetChangeCooldown = 0;
    private BlockPos closestBell;
    private int enterTimer = 4;

    @Override
    public void tick() {
        super.tick();

        if (!this.world.isClient && !this.dead) {
            // die in fire
            if (this.isOnFire()) {
                this.remove();
            }

            this.targetChangeCooldown -= (this.getPosVector().squaredDistanceTo(prevX, prevY, prevZ) < 0.0125) ? 10 : 1;

            // searching for the closest bell
            if (!world.isDaylight() || world.isRaining()) {
                if (closestBell != null) {
                    // verifying the bell still exists and is open
                    if (world.getBlockState(closestBell).getBlock() == IlluminationsBlocks.FAIRY_BELL) {
                        FairyBellBlock.State bellState = world.getBlockState(closestBell).get(FairyBellBlock.STATE);
                        if (bellState == FairyBellBlock.State.CLOSED) {
                            closestBell = null;
                        }
                    } else {
                        closestBell = null;
                    }
                    // if on same block, start timer to get inside, else reset it
                    if (closestBell != null && this.getBlockPos().equals(closestBell)) {
                        enterTimer--;
                    } else {
                        enterTimer = 4;
                    }
                    // if timer reaches 0, enter
                    if (enterTimer <= 0) {
                        world.setBlockState(closestBell,
                                IlluminationsBlocks.FAIRY_BELL.getDefaultState().with(FairyBellBlock.STATE, FairyBellBlock.State.CLOSED));
                        this.remove();
                    }
                }

                if (world.getTime() % 5 == 0 && closestBell == null) {
                    // detect block entities that are fairy bells
                    ArrayList<BlockPos> fairyBellPositions = new ArrayList<>();
                    world.blockEntities.forEach(blockEntity -> {
                        if (blockEntity instanceof FairyBellBlockEntity) {
                            FairyBellBlock.State bellState = world.getBlockState(blockEntity.getPos()).get(FairyBellBlock.STATE);
                            if (bellState == FairyBellBlock.State.EMPTY || bellState == FairyBellBlock.State.OPEN) {
                                fairyBellPositions.add(blockEntity.getPos());
                            }
                        }
                    });
                    // if no fairy bell, random block target
                    if (fairyBellPositions.size() > 0) {
                        if (closestBell == null) {
                            closestBell = fairyBellPositions.get(0);
                        }
                        // find the closest fairy bell
                        fairyBellPositions.forEach(blockPos -> {
                            if (closestBell != null) {
                                double distanceToClosest = this.getBlockPos().getSquaredDistance((double) closestBell.getX(), (double) closestBell.getY(), (double) closestBell.getZ(), true);
                                double distanceToBlockpos = this.getBlockPos().getSquaredDistance((double) blockPos.getX(), (double) blockPos.getY(), (double) blockPos.getZ(), true);
                                if (distanceToBlockpos < distanceToClosest) {
                                    closestBell = blockPos;
                                }
                            } else {
                                closestBell = blockPos;
                            }
                        });
                        // once found, set target
                        this.xTarget = closestBell.getX() + .5;
                        this.yTarget = closestBell.getY() + .5;
                        this.zTarget = closestBell.getZ() + .5;
                    } else if (closestBell == null) {
                        selectBlockTarget();
                    }
                }
            } else if ((xTarget == 0 && yTarget == 0 && zTarget == 0) || this.getPos().squaredDistanceTo(xTarget, yTarget, zTarget) < 9 || targetChangeCooldown <= 0) {
                selectBlockTarget();
            }

            Vec3d targetVector = new Vec3d(this.xTarget - x, this.yTarget - y, this.zTarget - z);
            double length = targetVector.length();
            targetVector = targetVector.multiply(0.1 / length);

            float f = new Random().nextFloat() / 2;
            if (!this.world.getBlockState(new BlockPos(this.x, this.y - 0.1, this.z)).getBlock().canMobSpawnInside()) {
                this.setVelocity((0.9) * getVelocity().x + (f) * targetVector.x,
                    0.05,
                    (0.9) * getVelocity().z + (f) * targetVector.z);
            } else this.setVelocity(
                    (0.9) * getVelocity().x + (f) * targetVector.x,
                    (0.9) * getVelocity().y + (f) * targetVector.y,
                    (0.9) * getVelocity().z + (f) * targetVector.z
            );
            if (this.getBlockPos() != this.getTargetPosition()) this.move(MovementType.SELF, this.getVelocity());
        }
    }

    private void selectBlockTarget() {
        this.groundLevel = 0;
        for (int i = 0; i < 20; i++) {
            BlockState checkedBlock = this.world.getBlockState(new BlockPos(this.x, this.y - i, this.z));
            if (!checkedBlock.getBlock().canMobSpawnInside()) {
                this.groundLevel = this.y - i;
            }
            if (this.groundLevel != 0) break;
        }

        this.xTarget = this.x + random.nextGaussian() * 10;
        this.yTarget = Math.min(Math.max(this.y + random.nextGaussian() * 2, this.groundLevel), this.groundLevel + 4);
        this.zTarget = this.z + random.nextGaussian() * 10;

        if (this.world.getBlockState(new BlockPos(this.xTarget, this.yTarget, this.zTarget)).getBlock().canMobSpawnInside())
            this.yTarget += 1;

        targetChangeCooldown = random.nextInt() % 100;
    }

    public BlockPos getTargetPosition() {
        return new BlockPos(this.xTarget, this.yTarget + 0.5, this.zTarget);
    }

    @Override
    public boolean canSpawn(IWorld world, SpawnType spawnType) {
        return this.world.isDaylight() && !this.world.isThundering();
    }

    @Override
    public void kill() {
        super.remove();
    }

    static {
        COLOR = DataTracker.registerData(FairyEntity.class, TrackedDataHandlerRegistry.INTEGER);
    }

    public int randomDualTone() {
        float r = 0;
        float g = 0;
        float b = 0;
        int colorToIgnore = new Random().nextInt(3);
        if (colorToIgnore != 0) {
            r = new Random().nextFloat();
        }
        if (colorToIgnore != 1) {
            g = new Random().nextFloat();
        }
        if (colorToIgnore != 2) {
            b = new Random().nextFloat();
        }
        return new Color(r, g, b).getRGB();
    }
}
