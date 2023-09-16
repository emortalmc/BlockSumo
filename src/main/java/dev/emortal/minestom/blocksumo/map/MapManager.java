package dev.emortal.minestom.blocksumo.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import dev.emortal.minestom.blocksumo.utils.RandomStringGenerator;
import dev.emortal.minestom.blocksumo.utils.gson.PosAdapter;
import net.hollowcube.polar.PolarLoader;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapManager.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(MapData.class, new MapData.Adapter())
            .registerTypeAdapter(Pos.class, new PosAdapter())
            .create();

    private static final DimensionType DIMENSION_TYPE = DimensionType.builder(NamespaceID.from("emortalmc:blocksumo"))
            .skylightEnabled(true)
            .ambientLight(1.0f)
            .build();

    private static final List<String> ENABLED_MAPS = List.of(
            "blocksumo",
            "castle",
//            "end",
            "icebs",
            "ruinsbs"
    );
    private static final Path MAPS_PATH = Path.of("maps");

    private final @NotNull Map<String, PreLoadedMap> preLoadedMaps;

    public MapManager() {
        MinecraftServer.getDimensionTypeManager().addDimension(DIMENSION_TYPE);

        Map<String, PreLoadedMap> maps = new HashMap<>();
        for (String mapName : ENABLED_MAPS) {
            Path mapPath = MAPS_PATH.resolve(mapName);
            Path polarPath = mapPath.resolve("map.polar");
            Path dataPath = mapPath.resolve("map_data.json");

            try {
                MapData mapData = GSON.fromJson(new JsonReader(new FileReader(dataPath.toFile())), MapData.class);
                LOGGER.info("Loaded map data for map {}: [{}]", mapName, mapData);

                PolarLoader polarLoader = new PolarLoader(polarPath);
//                if (!Files.exists(polarPath)) { // File needs to be converted
//                    PolarWorld world = AnvilPolar.anvilToPolar(mapPath, ChunkSelector.radius(CHUNK_LOADING_RADIUS));
//                    Files.write(polarPath, PolarWriter.write(world));
//                    polarLoader = new PolarLoader(world);
//                } else {
//                    polarLoader = new PolarLoader(polarPath);
//                }

                maps.put(mapName, new PreLoadedMap(polarLoader, mapData));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        this.preLoadedMaps = Map.copyOf(maps);
    }

    public @NotNull LoadedMap getMap(@Nullable String id) {
        if (id == null) {
            return this.getRandomMap();
        }

        PreLoadedMap map = this.preLoadedMaps.get(id);
        if (map == null) {
            LOGGER.warn("Map {} not found, loading random map", id);
            return this.getRandomMap();
        }

        return map.load();
    }

    public @NotNull LoadedMap getRandomMap() {
        String randomMapId = ENABLED_MAPS.get(ThreadLocalRandom.current().nextInt(ENABLED_MAPS.size()));

        PreLoadedMap map = this.preLoadedMaps.get(randomMapId);
        return map.load();
    }

    private record PreLoadedMap(@NotNull PolarLoader chunkLoader, @NotNull MapData mapData) {

        @NotNull LoadedMap load() {
            InstanceContainer instance = new InstanceContainer(UUID.randomUUID(), DIMENSION_TYPE, this.chunkLoader(), generateDimensionId());
            MinecraftServer.getInstanceManager().registerInstance(instance);

//            instance.enableAutoChunkLoad(false);
//            for (int x = -CHUNK_LOADING_RADIUS; x < CHUNK_LOADING_RADIUS; x++) {
//                for (int z = -CHUNK_LOADING_RADIUS; z < CHUNK_LOADING_RADIUS; z++) {
//                    instance.loadChunk(x, z);
//                }
//            }

            return new LoadedMap(instance, this.mapData());
        }

        private static @NotNull NamespaceID generateDimensionId() {
            String randomValue = RandomStringGenerator.generate(16);
            return NamespaceID.from("emortalmc", "dim_" + randomValue);
        }
    }
}