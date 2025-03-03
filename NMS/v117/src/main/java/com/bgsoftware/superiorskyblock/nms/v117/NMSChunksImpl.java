package com.bgsoftware.superiorskyblock.nms.v117;

import com.bgsoftware.common.reflection.ReflectField;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.key.KeyMap;
import com.bgsoftware.superiorskyblock.core.CalculatedChunk;
import com.bgsoftware.superiorskyblock.core.ChunkPosition;
import com.bgsoftware.superiorskyblock.core.SequentialListBuilder;
import com.bgsoftware.superiorskyblock.core.key.KeyImpl;
import com.bgsoftware.superiorskyblock.core.key.KeyMapImpl;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.nms.NMSChunks;
import com.bgsoftware.superiorskyblock.nms.v117.chunks.CropsBlockEntity;
import com.bgsoftware.superiorskyblock.world.generator.IslandsGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_17_R1.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v1_17_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class NMSChunksImpl implements NMSChunks {

    private static final ReflectField<Biome[]> BIOME_BASE_ARRAY = new ReflectField<>(
            ChunkBiomeContainer.class, Biome[].class, "f");
    private static final ReflectField<ChunkBiomeContainer> CHUNK_BIOME_CONTAINER = new ReflectField<>(
            LevelChunk.class, ChunkBiomeContainer.class, Modifier.PRIVATE, 1);

    private final SuperiorSkyblockPlugin plugin;

    public NMSChunksImpl(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setBiome(List<ChunkPosition> chunkPositions, org.bukkit.block.Biome bukkitBiome, Collection<Player> playersToUpdate) {
        if (chunkPositions.isEmpty())
            return;

        List<ChunkPos> chunksCoords = new SequentialListBuilder<ChunkPos>()
                .build(chunkPositions, chunkPosition -> new ChunkPos(chunkPosition.getX(), chunkPosition.getZ()));

        ServerLevel serverLevel = ((CraftWorld) chunkPositions.get(0).getWorld()).getHandle();
        Registry<Biome> biomesRegistry = serverLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);

        Biome biome = CraftBlock.biomeToBiomeBase(biomesRegistry, bukkitBiome);

        NMSUtils.runActionOnChunks(serverLevel, chunksCoords, true, null, levelChunk -> {
            ChunkPos chunkPos = levelChunk.getPos();
            Biome[] biomes = BIOME_BASE_ARRAY.get(levelChunk.getBiomes());

            if (biomes == null)
                throw new RuntimeException("Error while receiving biome bases of chunk (" + chunkPos.x + "," + chunkPos.z + ").");

            Arrays.fill(biomes, biome);
            levelChunk.setUnsaved(true);

            ClientboundForgetLevelChunkPacket forgetLevelChunkPacket = new ClientboundForgetLevelChunkPacket(chunkPos.x, chunkPos.z);
            //noinspection deprecation
            ClientboundLevelChunkPacket levelChunkPacket = new ClientboundLevelChunkPacket(levelChunk);
            ClientboundLightUpdatePacket lightUpdatePacket = new ClientboundLightUpdatePacket(chunkPos,
                    serverLevel.getLightEngine(), null, null, true);

            playersToUpdate.forEach(player -> {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                serverPlayer.connection.send(forgetLevelChunkPacket);
                serverPlayer.connection.send(lightUpdatePacket);
                serverPlayer.connection.send(levelChunkPacket);
            });
        }, (chunkCoords, unloadedChunk) -> {
            int[] biomes = unloadedChunk.contains("Biomes", 11) ? unloadedChunk.getIntArray("Biomes") : new int[256];
            Arrays.fill(biomes, biomesRegistry.getId(biome));
            unloadedChunk.putIntArray("Biomes", biomes);
        });
    }

    @Override
    public void deleteChunks(Island island, List<ChunkPosition> chunkPositions, Runnable onFinish) {
        if (chunkPositions.isEmpty())
            return;

        List<ChunkPos> chunksCoords = new SequentialListBuilder<ChunkPos>()
                .build(chunkPositions, chunkPosition -> new ChunkPos(chunkPosition.getX(), chunkPosition.getZ()));

        chunkPositions.forEach(chunkPosition -> island.markChunkEmpty(chunkPosition.getWorld(),
                chunkPosition.getX(), chunkPosition.getZ(), false));

        ServerLevel serverLevel = ((CraftWorld) chunkPositions.get(0).getWorld()).getHandle();

        NMSUtils.runActionOnChunks(serverLevel, chunksCoords, true, onFinish, levelChunk -> {
            Arrays.fill(levelChunk.getSections(), LevelChunk.EMPTY_SECTION);

            removeEntities(levelChunk);

            new HashSet<>(levelChunk.getBlockEntities().keySet()).forEach(levelChunk.getLevel()::removeBlockEntity);
            levelChunk.getBlockEntities().clear();

            removeBlocks(levelChunk);
        }, (chunkCoords, levelCompound) -> {
            ListTag sectionsList = new ListTag();
            ListTag tileEntities = new ListTag();

            levelCompound.put("Sections", sectionsList);
            levelCompound.put("TileEntities", tileEntities);
            levelCompound.put("Entities", new ListTag());

            if (!(serverLevel.generator instanceof IslandsGenerator)) {
                ProtoChunk protoChunk = NMSUtils.createProtoChunk(chunkCoords, serverLevel);

                try {
                    CustomChunkGenerator customChunkGenerator = new CustomChunkGenerator(serverLevel,
                            serverLevel.getChunkSource().getGenerator(), serverLevel.generator);

                    WorldGenRegion region = new WorldGenRegion(serverLevel, Collections.singletonList(protoChunk),
                            ChunkStatus.SURFACE, 0);

                    customChunkGenerator.buildSurface(region, protoChunk);
                } catch (Exception ignored) {
                }

                LevelLightEngine lightEngine = serverLevel.getLightEngine();
                LevelChunkSection[] levelChunkSections = protoChunk.getSections();

                for (int i = lightEngine.getMinLightSection(); i < lightEngine.getMaxLightSection(); ++i) {
                    for (LevelChunkSection levelChunkSection : levelChunkSections) {
                        if (levelChunkSection != LevelChunk.EMPTY_SECTION && levelChunkSection.bottomBlockY() >> 4 == i) {
                            CompoundTag sectionCompound = new CompoundTag();
                            sectionCompound.putByte("Y", (byte) (i & 255));
                            levelChunkSection.getStates().write(sectionCompound, "Palette", "BlockStates");
                            sectionsList.add(sectionCompound);
                        }
                    }
                }

                for (BlockPos blockEntityPos : protoChunk.getBlockEntitiesPos()) {
                    CompoundTag blockEntityCompound = protoChunk.getBlockEntityNbt(blockEntityPos);
                    if (blockEntityCompound != null)
                        tileEntities.add(blockEntityCompound);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<CalculatedChunk>> calculateChunks(List<ChunkPosition> chunkPositions,
                                                                    Map<ChunkPosition, CalculatedChunk> unloadedChunksCache) {
        List<CalculatedChunk> allCalculatedChunks = new LinkedList<>();
        List<ChunkPos> chunksCoords = new LinkedList<>();

        Iterator<ChunkPosition> chunkPositionsIterator = chunkPositions.iterator();
        while (chunkPositionsIterator.hasNext()) {
            ChunkPosition chunkPosition = chunkPositionsIterator.next();
            CalculatedChunk cachedCalculatedChunk = unloadedChunksCache.get(chunkPosition);
            if (cachedCalculatedChunk != null) {
                allCalculatedChunks.add(cachedCalculatedChunk);
                chunkPositionsIterator.remove();
            } else {
                chunksCoords.add(new ChunkPos(chunkPosition.getX(), chunkPosition.getZ()));
            }
        }

        if (chunkPositions.isEmpty())
            return CompletableFuture.completedFuture(allCalculatedChunks);

        CompletableFuture<List<CalculatedChunk>> completableFuture = new CompletableFuture<>();

        ServerLevel serverLevel = ((CraftWorld) chunkPositions.get(0).getWorld()).getHandle();

        NMSUtils.runActionOnChunks(serverLevel, chunksCoords, false, () -> {
            completableFuture.complete(allCalculatedChunks);
        }, levelChunk -> {
            ChunkPos chunkPos = levelChunk.getPos();
            ChunkPosition chunkPosition = ChunkPosition.of(serverLevel.getWorld(), chunkPos.x, chunkPos.z);
            allCalculatedChunks.add(calculateChunk(chunkPosition, levelChunk.getSections()));
        }, (chunkPos, unloadedChunk) -> {
            ListTag sectionsList = unloadedChunk.getList("Sections", 10);
            LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionsList.size()];

            for (int i = 0; i < sectionsList.size(); ++i) {
                CompoundTag sectionCompound = sectionsList.getCompound(i);
                byte yPosition = sectionCompound.getByte("Y");
                if (sectionCompound.contains("Palette", 9) && sectionCompound.contains("BlockStates", 12)) {
                    //noinspection deprecation
                    levelChunkSections[i] = new LevelChunkSection(yPosition);
                    levelChunkSections[i].getStates().read(sectionCompound.getList("Palette", 10),
                            sectionCompound.getLongArray("BlockStates"));
                }
            }

            ChunkPosition chunkPosition = ChunkPosition.of(serverLevel.getWorld(), chunkPos.x, chunkPos.z);
            CalculatedChunk calculatedChunk = calculateChunk(chunkPosition, levelChunkSections);
            allCalculatedChunks.add(calculatedChunk);
            unloadedChunksCache.put(chunkPosition, calculatedChunk);
        });

        return completableFuture;
    }

    @Override
    public void injectChunkSections(org.bukkit.Chunk chunk) {
        // No implementation
    }

    @Override
    public boolean isChunkEmpty(org.bukkit.Chunk bukkitChunk) {
        LevelChunk levelChunk = ((CraftChunk) bukkitChunk).getHandle();
        return Arrays.stream(levelChunk.getSections()).allMatch(chunkSection ->
                chunkSection == LevelChunk.EMPTY_SECTION || chunkSection.isEmpty());
    }

    @Override
    public org.bukkit.Chunk getChunkIfLoaded(ChunkPosition chunkPosition) {
        ServerLevel serverLevel = ((CraftWorld) chunkPosition.getWorld()).getHandle();
        ChunkAccess chunkAccess = serverLevel.getChunkSource().getChunk(chunkPosition.getX(), chunkPosition.getZ(), false);
        return chunkAccess instanceof LevelChunk levelChunk ? levelChunk.getBukkitChunk() : null;
    }

    @Override
    public void startTickingChunk(Island island, org.bukkit.Chunk chunk, boolean stop) {
        if (plugin.getSettings().getCropsInterval() <= 0)
            return;

        LevelChunk levelChunk = ((CraftChunk) chunk).getHandle();

        if (stop) {
            CropsBlockEntity cropsBlockEntity = CropsBlockEntity.remove(levelChunk.getPos());
            if (cropsBlockEntity != null)
                cropsBlockEntity.remove();
        } else {
            CropsBlockEntity.create(island, levelChunk);
        }
    }

    @Override
    public void updateCropsTicker(List<ChunkPosition> chunkPositions, double newCropGrowthMultiplier) {
        if (chunkPositions.isEmpty()) return;
        CropsBlockEntity.forEachChunk(chunkPositions, cropsBlockEntity ->
                cropsBlockEntity.setCropGrowthMultiplier(newCropGrowthMultiplier));
    }

    @Override
    public void shutdown() {
        List<CompletableFuture<Void>> pendingTasks = NMSUtils.getPendingChunkActions();

        if (pendingTasks.isEmpty())
            return;

        Log.info("Waiting for chunk tasks to complete.");

        CompletableFuture.allOf(pendingTasks.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public List<Location> getBlockEntities(Chunk chunk) {
        LevelChunk levelChunk = ((CraftChunk) chunk).getHandle();
        List<Location> blockEntities = new LinkedList<>();

        World bukkitWorld = chunk.getWorld();

        levelChunk.getBlockEntities().keySet().forEach(blockPos ->
                blockEntities.add(new Location(bukkitWorld, blockPos.getX(), blockPos.getY(), blockPos.getZ())));

        return blockEntities;
    }

    private static CalculatedChunk calculateChunk(ChunkPosition chunkPosition, LevelChunkSection[] chunkSections) {
        KeyMap<Integer> blockCounts = KeyMapImpl.createHashMap();
        Set<Location> spawnersLocations = new HashSet<>();

        for (LevelChunkSection levelChunkSection : chunkSections) {
            if (levelChunkSection != LevelChunk.EMPTY_SECTION) {
                for (BlockPos blockPos : BlockPos.betweenClosed(0, 0, 0, 15, 15, 15)) {
                    BlockState blockState = levelChunkSection.getBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    Block block = blockState.getBlock();

                    if (block == Blocks.AIR)
                        continue;

                    Location location = new Location(chunkPosition.getWorld(),
                            (chunkPosition.getX() << 4) + blockPos.getX(),
                            levelChunkSection.bottomBlockY() + blockPos.getY(),
                            (chunkPosition.getZ() << 4) + blockPos.getZ());

                    int blockAmount = 1;

                    if (NMSUtils.isDoubleBlock(block, blockState)) {
                        blockAmount = 2;
                        blockState = blockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    }

                    Material type = CraftMagicNumbers.getMaterial(blockState.getBlock());
                    Key blockKey = KeyImpl.of(type.name() + "", "0", location);
                    blockCounts.put(blockKey, blockCounts.getOrDefault(blockKey, 0) + blockAmount);
                    if (type == Material.SPAWNER) {
                        spawnersLocations.add(location);
                    }
                }
            }
        }

        return new CalculatedChunk(chunkPosition, blockCounts, spawnersLocations);
    }

    private static void removeEntities(LevelChunk levelChunk) {
        ChunkPos chunkPos = levelChunk.getPos();
        ServerLevel serverLevel = levelChunk.level;

        int chunkWorldCoordX = chunkPos.x << 4;
        int chunkWorldCoordZ = chunkPos.z << 4;


        AABB chunkBounds = new AABB(chunkWorldCoordX, serverLevel.getMinBuildHeight(), chunkWorldCoordZ,
                chunkWorldCoordX + 15, serverLevel.getMaxBuildHeight(), chunkWorldCoordZ + 15);

        Iterator<Entity> chunkEntities;

        try {
            chunkEntities = levelChunk.entities.iterator();
        } catch (Throwable ex) {
            List<Entity> worldEntities = new LinkedList<>();
            serverLevel.getEntities().get(chunkBounds, worldEntities::add);
            chunkEntities = worldEntities.iterator();
        }

        while (chunkEntities.hasNext()) {
            Entity entity = chunkEntities.next();
            if (!(entity instanceof net.minecraft.world.entity.player.Player))
                entity.setRemoved(Entity.RemovalReason.DISCARDED);
        }
    }

    private static void removeBlocks(LevelChunk levelChunk) {
        ServerLevel serverLevel = levelChunk.level;

        ChunkGenerator bukkitGenerator = serverLevel.getWorld().getGenerator();

        if (bukkitGenerator != null && !(bukkitGenerator instanceof IslandsGenerator)) {
            CustomChunkGenerator chunkGenerator = new CustomChunkGenerator(serverLevel,
                    serverLevel.getChunkSource().getGenerator(),
                    bukkitGenerator);

            WorldGenRegion region = new WorldGenRegion(serverLevel, Collections.singletonList(levelChunk),
                    ChunkStatus.SURFACE, 0);

            try {
                chunkGenerator.buildSurface(region, levelChunk);
            } catch (ClassCastException error) {
                ProtoChunk protoChunk = NMSUtils.createProtoChunk(levelChunk.getPos(), serverLevel);
                chunkGenerator.buildSurface(region, protoChunk);

                // Load chunk sections from proto chunk to the actual chunk
                for (int i = 0; i < protoChunk.getSections().length && i < levelChunk.getSections().length; ++i) {
                    levelChunk.getSections()[i] = protoChunk.getSections()[i];
                }

                // Load biomes from proto chunk to the actual chunk
                if (protoChunk.getBiomes() != null)
                    CHUNK_BIOME_CONTAINER.set(levelChunk, protoChunk.getBiomes());

                // Load tile entities from proto chunk to the actual chunk
                protoChunk.getBlockEntities().forEach(((blockPosition, tileEntity) -> levelChunk.setBlockEntity(tileEntity)));
            }
        }
    }

}
