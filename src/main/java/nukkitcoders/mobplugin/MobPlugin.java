package nukkitcoders.mobplugin;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import nukkitcoders.mobplugin.entities.BaseEntity;
import nukkitcoders.mobplugin.entities.animal.flying.Bat;
import nukkitcoders.mobplugin.entities.animal.flying.Parrot;
import nukkitcoders.mobplugin.entities.animal.jumping.Rabbit;
import nukkitcoders.mobplugin.entities.animal.swimming.*;
import nukkitcoders.mobplugin.entities.animal.walking.*;
import nukkitcoders.mobplugin.entities.block.BlockEntitySpawner;
import nukkitcoders.mobplugin.entities.monster.flying.*;
import nukkitcoders.mobplugin.entities.monster.jumping.MagmaCube;
import nukkitcoders.mobplugin.entities.monster.jumping.Slime;
import nukkitcoders.mobplugin.entities.monster.swimming.ElderGuardian;
import nukkitcoders.mobplugin.entities.monster.swimming.Guardian;
import nukkitcoders.mobplugin.entities.monster.walking.*;
import nukkitcoders.mobplugin.entities.projectile.*;
import nukkitcoders.mobplugin.event.entity.SpawnGolemEvent;
import nukkitcoders.mobplugin.event.spawner.SpawnerChangeTypeEvent;
import nukkitcoders.mobplugin.event.spawner.SpawnerCreateEvent;
import nukkitcoders.mobplugin.utils.Utils;

import static nukkitcoders.mobplugin.entities.block.BlockEntitySpawner.*;

/**
 * @author <a href="mailto:kniffman@googlemail.com">Michael Gertz (kniffo80)</a>
 */
public class MobPlugin extends PluginBase implements Listener {

    public Config pluginConfig = null;

    private static MobPlugin instance;

    public static MobPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        pluginConfig = getConfig();

        if (getConfig().getInt("config-version") != 8) {
            this.getServer().getLogger().warning("MobPlugin's config file is outdated. Please delete old config.");
            this.getServer().getPluginManager().disablePlugin(this);
        }

        int spawnDelay = pluginConfig.getInt("entities.autospawn-ticks", 0);

        if (spawnDelay > 0) {
            this.getServer().getScheduler().scheduleDelayedRepeatingTask(this, new AutoSpawnTask(this), spawnDelay, spawnDelay);
        }

        this.getServer().getPluginManager().registerEvents(this, this);
        this.registerEntities();
    }

    @Override
    public void onDisable() {
        RouteFinderThreadPool.shutDownNow();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().toLowerCase().equals("mob")) return true;

        if (args.length == 0) {
            sender.sendMessage("-- MobPlugin " + this.getDescription().getVersion() + " --");
            sender.sendMessage("/mob spawn <entity> <opt:player> - Summon entity");
            sender.sendMessage("/mob removeall - Remove all living mobs");
            sender.sendMessage("/mob removeitems - Remove all items from ground");
            return true;
        }

        switch (args[0]) {
            case "spawn":
                if (args.length == 1) {
                    sender.sendMessage("Usage: /mob spawn <entity> <opt:player>");
                    break;
                }

                String mob = args[1];
                Player playerThatSpawns;

                if (args.length == 3) {
                    playerThatSpawns = this.getServer().getPlayer(args[2]);
                } else {
                    playerThatSpawns = (Player) sender;
                }

                if (playerThatSpawns != null) {
                    Position pos = playerThatSpawns.getPosition();

                    Entity ent;
                    if ((ent = MobPlugin.create(mob, pos)) != null) {
                        ent.spawnToAll();
                        sender.sendMessage("Spawned " + mob + " to " + playerThatSpawns.getName());
                    } else {
                        sender.sendMessage("Unable to spawn " + mob);
                    }
                } else {
                    sender.sendMessage("Unknown player " + (args.length == 3 ? args[2] : sender.getName()));
                }
                break;
            case "removeall":
                int count = 0;
                for (Level level : getServer().getLevels().values()) {
                    for (Entity entity : level.getEntities()) {
                        if (entity instanceof BaseEntity) {
                            entity.close();
                            ++count;
                        }
                    }
                }
                sender.sendMessage("Removed " + count + " entities from all levels.");
                break;
            case "removeitems":
                count = 0;
                for (Level level : getServer().getLevels().values()) {
                    for (Entity entity : level.getEntities()) {
                        if (entity instanceof EntityItem && entity.isOnGround()) {
                            entity.close();
                            ++count;
                        }
                    }
                }
                sender.sendMessage("Removed " + count + " items on ground from all levels.");
                break;
            default:
                sender.sendMessage("Unknown command.");
                break;
        }
        return true;

    }

    private void registerEntities() {
        BlockEntity.registerBlockEntity("MobSpawner", BlockEntitySpawner.class);

        Entity.registerEntity(Bat.class.getSimpleName(), Bat.class);
        Entity.registerEntity(Cat.class.getSimpleName(), Cat.class);
        Entity.registerEntity(Chicken.class.getSimpleName(), Chicken.class);
        Entity.registerEntity(Cod.class.getSimpleName(), Cod.class);
        Entity.registerEntity(Cow.class.getSimpleName(), Cow.class);
        Entity.registerEntity(Dolphin.class.getSimpleName(), Dolphin.class);
        Entity.registerEntity(Donkey.class.getSimpleName(), Donkey.class);
        Entity.registerEntity(Horse.class.getSimpleName(), Horse.class);
        Entity.registerEntity(MagmaCube.class.getSimpleName(), MagmaCube.class);
        Entity.registerEntity(Llama.class.getSimpleName(), Llama.class);
        Entity.registerEntity(Mooshroom.class.getSimpleName(), Mooshroom.class);
        Entity.registerEntity(Mule.class.getSimpleName(), Mule.class);
        Entity.registerEntity(Ocelot.class.getSimpleName(), Ocelot.class);
        Entity.registerEntity(Panda.class.getSimpleName(), Panda.class);
        Entity.registerEntity(Parrot.class.getSimpleName(), Parrot.class);
        Entity.registerEntity(Pig.class.getSimpleName(), Pig.class);
        Entity.registerEntity(PolarBear.class.getSimpleName(), PolarBear.class);
        Entity.registerEntity(Pufferfish.class.getSimpleName(), Pufferfish.class);
        Entity.registerEntity(Rabbit.class.getSimpleName(), Rabbit.class);
        Entity.registerEntity(Salmon.class.getSimpleName(), Salmon.class);
        Entity.registerEntity(SkeletonHorse.class.getSimpleName(), SkeletonHorse.class);
        Entity.registerEntity(Sheep.class.getSimpleName(), Sheep.class);
        Entity.registerEntity(Squid.class.getSimpleName(), Squid.class);
        Entity.registerEntity(TropicalFish.class.getSimpleName(), TropicalFish.class);
        Entity.registerEntity(Turtle.class.getSimpleName(), Turtle.class);
        Entity.registerEntity(Villager.class.getSimpleName(), Villager.class);
        Entity.registerEntity(ZombieHorse.class.getSimpleName(), ZombieHorse.class);
        Entity.registerEntity(WanderingTrader.class.getSimpleName(), WanderingTrader.class);

        Entity.registerEntity(Blaze.class.getSimpleName(), Blaze.class);
        Entity.registerEntity(Ghast.class.getSimpleName(), Ghast.class);
        Entity.registerEntity(CaveSpider.class.getSimpleName(), CaveSpider.class);
        Entity.registerEntity(WitherSkeleton.class.getSimpleName(), WitherSkeleton.class);
        Entity.registerEntity(Creeper.class.getSimpleName(), Creeper.class);
        Entity.registerEntity(Drowned.class.getSimpleName(), Drowned.class);
        Entity.registerEntity(ElderGuardian.class.getSimpleName(), ElderGuardian.class);
        Entity.registerEntity(EnderDragon.class.getSimpleName(), EnderDragon.class);
        Entity.registerEntity(Enderman.class.getSimpleName(), Enderman.class);
        Entity.registerEntity(Endermite.class.getSimpleName(), Endermite.class);
        Entity.registerEntity(Evoker.class.getSimpleName(), Evoker.class);
        Entity.registerEntity(Guardian.class.getSimpleName(), Guardian.class);
        Entity.registerEntity(Husk.class.getSimpleName(), Husk.class);
        Entity.registerEntity(IronGolem.class.getSimpleName(), IronGolem.class);
        Entity.registerEntity(Phantom.class.getSimpleName(), Phantom.class);
        Entity.registerEntity(ZombiePigman.class.getSimpleName(), ZombiePigman.class);
        Entity.registerEntity(Shulker.class.getSimpleName(), Shulker.class);
        Entity.registerEntity(Silverfish.class.getSimpleName(), Silverfish.class);
        Entity.registerEntity(Skeleton.class.getSimpleName(), Skeleton.class);
        Entity.registerEntity(Slime.class.getSimpleName(), Slime.class);
        Entity.registerEntity(SnowGolem.class.getSimpleName(), SnowGolem.class);
        Entity.registerEntity(Spider.class.getSimpleName(), Spider.class);
        Entity.registerEntity(Stray.class.getSimpleName(), Stray.class);
        Entity.registerEntity(Vex.class.getSimpleName(), Vex.class);
        Entity.registerEntity(Vindicator.class.getSimpleName(), Vindicator.class);
        Entity.registerEntity(Witch.class.getSimpleName(), Witch.class);
        Entity.registerEntity(Wither.class.getSimpleName(), Wither.class);
        Entity.registerEntity(Wolf.class.getSimpleName(), Wolf.class);
        Entity.registerEntity(Zombie.class.getSimpleName(), Zombie.class);
        Entity.registerEntity(ZombieVillager.class.getSimpleName(), ZombieVillager.class);
        Entity.registerEntity(Pillager.class.getSimpleName(), Pillager.class);
        Entity.registerEntity(Ravager.class.getSimpleName(), Ravager.class);

        Entity.registerEntity("BlueWitherSkull", EntityBlueWitherSkull.class);
        Entity.registerEntity("FireBall", EntityFireBall.class);
        Entity.registerEntity("ShulkerBullet", EntityShulkerBullet.class);
    }

    public static Entity create(Object type, Position source, Object... args) {
        FullChunk chunk = source.getLevel().getChunk((int) source.x >> 4, (int) source.z >> 4, true);

        CompoundTag nbt = new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", source.x)).add(new DoubleTag("", source.y)).add(new DoubleTag("", source.z)))
                .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", source instanceof Location ? (float) ((Location) source).yaw : 0))
                        .add(new FloatTag("", source instanceof Location ? (float) ((Location) source).pitch : 0)));

        return Entity.createEntity(type.toString(), chunk, nbt, args);
    }

    @EventHandler
    public void EntityDeathEvent(EntityDeathEvent ev) {
        if (!(ev.getEntity() instanceof BaseEntity)) return;
        BaseEntity baseEntity = (BaseEntity) ev.getEntity();
        if (!(baseEntity.getLastDamageCause() instanceof EntityDamageByEntityEvent)) return;
        Entity damager = ((EntityDamageByEntityEvent) baseEntity.getLastDamageCause()).getDamager();
        if (!(damager instanceof Player)) return;
        int killExperience = baseEntity.getKillExperience();
        if (killExperience > 0) {
            damager.getLevel().dropExpOrb(baseEntity, killExperience);
        }
    }

    @EventHandler
    public void PlayerInteractEvent(PlayerInteractEvent ev) {
        if (ev.getFace() == null || ev.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        Item item = ev.getItem();
        Block block = ev.getBlock();
        Player player = ev.getPlayer();

        if (item.getId() != Item.SPAWN_EGG || block.getId() != Block.MONSTER_SPAWNER) return;

        BlockEntity blockEntity = block.getLevel().getBlockEntity(block);
        if (blockEntity instanceof BlockEntitySpawner) {
            SpawnerChangeTypeEvent event = new SpawnerChangeTypeEvent((BlockEntitySpawner) blockEntity, ev.getBlock(), ev.getPlayer(), ((BlockEntitySpawner) blockEntity).getSpawnEntityType(), item.getDamage());
            this.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            ((BlockEntitySpawner) blockEntity).setSpawnEntityType(item.getDamage());
            ev.setCancelled(true);

            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }
        } else {
            SpawnerCreateEvent event = new SpawnerCreateEvent(ev.getPlayer(), ev.getBlock(), item.getDamage());
            this.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            ev.setCancelled(true);
            if (blockEntity != null) {
                blockEntity.close();
            }
            CompoundTag nbt = new CompoundTag()
                    .putString(TAG_ID, BlockEntity.MOB_SPAWNER)
                    .putInt(TAG_ENTITY_ID, item.getDamage())
                    .putInt(TAG_X, (int) block.x)
                    .putInt(TAG_Y, (int) block.y)
                    .putInt(TAG_Z, (int) block.z);
            new BlockEntitySpawner(block.getLevel().getChunk((int) block.x >> 4, (int) block.z >> 4), nbt);

            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void BlockPlaceEvent(BlockPlaceEvent ev) {
        Block block = ev.getBlock();
        Player player = ev.getPlayer();
        if (block.getId() == Block.JACK_O_LANTERN || block.getId() == Block.PUMPKIN) {
            if (block.getSide(BlockFace.DOWN).getId() == Item.SNOW_BLOCK && block.getSide(BlockFace.DOWN, 2).getId() == Item.SNOW_BLOCK) {

                SpawnGolemEvent event = new SpawnGolemEvent(player, block.add(0.5, -1, 0.5), SpawnGolemEvent.GolemType.SNOW_GOLEM);

                this.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) return;

                Entity entity = create(SnowGolem.NETWORK_ID, block.add(0.5, -1, 0.5));

                if (entity != null) entity.spawnToAll();

                block.level.setBlock(block.add(0, -1, 0), new BlockAir());
                block.level.setBlock(block.add(0, -2, 0), new BlockAir());

                ev.setCancelled();
                if (player.isSurvival()) player.getInventory().removeItem(Item.get(block.getId()));
            } else if (block.getSide(BlockFace.DOWN).getId() == Item.IRON_BLOCK && block.getSide(BlockFace.DOWN, 2).getId() == Item.IRON_BLOCK) {
                int removeId = block.getId();
                block = block.getSide(BlockFace.DOWN);

                Block first = null, second = null;
                if (block.getSide(BlockFace.EAST).getId() == Item.IRON_BLOCK && block.getSide(BlockFace.WEST).getId() == Item.IRON_BLOCK) {
                    first = block.getSide(BlockFace.EAST);
                    second = block.getSide(BlockFace.WEST);
                } else if (block.getSide(BlockFace.NORTH).getId() == Item.IRON_BLOCK && block.getSide(BlockFace.SOUTH).getId() == Item.IRON_BLOCK) {
                    first = block.getSide(BlockFace.NORTH);
                    second = block.getSide(BlockFace.SOUTH);
                }

                if (second == null || first == null) return;

                SpawnGolemEvent event = new SpawnGolemEvent(player, block.add(0.5, -1, 0.5), SpawnGolemEvent.GolemType.IRON_GOLEM);

                this.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) return;

                Entity entity = create(IronGolem.NETWORK_ID, block.add(0.5, -1, 0.5));

                if (entity != null) entity.spawnToAll();

                block.level.setBlock(first, new BlockAir());
                block.level.setBlock(second, new BlockAir());
                block.level.setBlock(block, new BlockAir());
                block.level.setBlock(block.add(0, -1, 0), new BlockAir());

                ev.setCancelled();
                if (player.isSurvival()) player.getInventory().removeItem(Item.get(removeId));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void BlockBreakEvent(BlockBreakEvent ev) {
        Block block = ev.getBlock();
        if ((block.getId() == Block.MONSTER_EGG) && block.level.getBlockLightAt((int) block.x, (int) block.y, (int) block.z) < 12 && Utils.rand(1, 5) == 1) {

            Silverfish entity = (Silverfish) create(Silverfish.NETWORK_ID, block.add(0.5, 0, 0.5));
            if (entity == null) return;

            entity.spawnToAll();
            EntityEventPacket pk = new EntityEventPacket();
            pk.eid = entity.getId();
            pk.event = 27;
            entity.level.addChunkPacket(entity.getChunkX() >> 4, entity.getChunkZ() >> 4, pk);
        }
    }

    @EventHandler
    public void InventoryPickupArrowEvent(InventoryPickupArrowEvent ev) {
        if (ev.getArrow().namedTag.getBoolean("canNotPickup")) {
            ev.setCancelled();
        }
    }
}
