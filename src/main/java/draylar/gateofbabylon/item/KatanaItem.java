package draylar.gateofbabylon.item;

import draylar.gateofbabylon.GateOfBabylon;
import draylar.gateofbabylon.enchantment.KatanaSlashEnchantment;
import draylar.gateofbabylon.registry.GOBSounds;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KatanaItem extends SwordItem {

    private final float attackDamage;

    public KatanaItem(ToolMaterial material, float effectiveDamage, float effectiveSpeed) {
        super(material, (int) effectiveDamage - 1, -4 + effectiveSpeed, new Item.Settings().group(GateOfBabylon.GROUP).maxCount(1));
        attackDamage = effectiveDamage;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (itemStack.getDamage() >= itemStack.getMaxDamage() - 1) {
            return TypedActionResult.fail(itemStack);
        } else {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(itemStack);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) user;
            int currentUseTime = this.getMaxUseTime(stack) - remainingUseTicks;
            KatanaSlashEnchantment enchantment = getSlashEnchantment(stack);

            if (currentUseTime >= 10) {
                if (!world.isClient) {
                    ServerWorld serverWorld = (ServerWorld) world;

                    stack.damage(1, player, entity -> entity.sendToolBreakStatus(user.getActiveHand()));
                    HitResult rayTrace = raycast(user, 16, 0, false);

                    // Play SFX
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), GOBSounds.KATANA_SWOOP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    if(enchantment != null) {
                        world.playSound(null, user.getX(), user.getY(), user.getZ(), enchantment.getSound(), SoundCategory.PLAYERS, 2.0F, 0.25F);
                    }

                    // calculate line from player to target
                    Vec3d distanceVec = rayTrace.getPos().subtract(user.getPos());
                    double distance = Math.sqrt(Math.pow(distanceVec.getX(), 2) + Math.pow(distanceVec.getY(), 2) + Math.pow(distanceVec.getZ(), 2)); // distance from player to target
                    Vec3d addPerBlock = distanceVec.multiply(1 / distance);
                    Vec3d currentPos = user.getPos().add(0, 0, 0);

                    // store hit entities
                    List<UUID> hitEntities = new ArrayList<>();

                    // iterate over each block between player and target
                    for(int i = 0; i  < distance; i++) {
                        serverWorld.spawnParticles(ParticleTypes.CRIT, currentPos.getX(), currentPos.getY() + .5, currentPos.getZ(), 5, 0, 0, 0, .1);
                        currentPos = currentPos.add(addPerBlock);

                        if(enchantment != null) {
                            serverWorld.spawnParticles(enchantment.getParticle(), currentPos.getX(), currentPos.getY() + .5, currentPos.getZ(), 5, 0, 0, 0, .1);
                        }

                        // check for small box around the current position for enemies
                        world.getEntitiesByClass(HostileEntity.class, new Box(currentPos.add(-2, -2, -2), currentPos.add(2, 2, 2)), entity -> !hitEntities.contains(entity.getUuid())).forEach(entity -> {
                            entity.damage(DamageSource.player((PlayerEntity) user), getAttackDamage() / 2);

                            if(enchantment != null) {
                                enchantment.onHit(entity, player, stack);
                            }

                            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), GOBSounds.KATANA_SWOOP, SoundCategory.PLAYERS, 2F, 1.5F + (float) world.random.nextDouble() * .5f);
                            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5F, 1.5F + (float) world.random.nextDouble() * .5f);
                            serverWorld.spawnParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + .5, entity.getZ(), 25, 0, 0, 0, .1);
                            hitEntities.add(entity.getUuid());
                        });
                    }

                    int down = 1;
                    if(!world.getBlockState(new BlockPos(rayTrace.getPos().x, rayTrace.getPos().y - 1, rayTrace.getPos().z)).isAir()) {
                        down = 0;
                    }

                    // Teleport forwards
                    user.requestTeleport(rayTrace.getPos().getX(), rayTrace.getPos().getY() - down, rayTrace.getPos().getZ());
                }

                player.getItemCooldownManager().set(this, 20 * 10); // 10 second cd
                player.incrementStat(Stats.USED.getOrCreateStat(this));
            }
        }
    }

    public KatanaSlashEnchantment getSlashEnchantment(ItemStack stack) {
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.get(stack).entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment instanceof KatanaSlashEnchantment) {
                return (KatanaSlashEnchantment) enchantment;
            }
        }

        return null;
    }

    public float getAttackDamage() {
        return attackDamage;
    }

    public HitResult raycast(Entity from, double maxDistance, float tickDelta, boolean includeFluids) {
        Vec3d cameraPosVec = from.getCameraPosVec(tickDelta);
        Vec3d rotationVec = from.getRotationVec(tickDelta);
        Vec3d vec3d3 = cameraPosVec.add(rotationVec.x * maxDistance, 0 * maxDistance, rotationVec.z * maxDistance);
        return from.world.raycast(new RaycastContext(cameraPosVec, vec3d3, RaycastContext.ShapeType.OUTLINE, includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE, from));
    }
}
