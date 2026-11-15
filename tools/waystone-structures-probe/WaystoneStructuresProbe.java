import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Disposable dedicated-server probe: places the Structory Towers, nova_structures shrine and Trek village
 * structures the way /place structure does, then records the Waystones blocks and pool pieces they contain.
 * Writes waystone-structures-probe.json into the server directory once the server has started.
 */
public final class WaystoneStructuresProbe implements ModInitializer {
	private static final String OUTPUT = "waystone-structures-probe.json";
	private static final String MARKER = "WAYSTONE_STRUCTURES_PROBE";
	private static final String PLAZA = "lampas:village/waystone_plaza";
	private static final int SPACING = 512;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(WaystoneStructuresProbe::probe);
	}

	private static void probe(MinecraftServer server) {
		int attempts = Integer.getInteger("waystoneProbe.attempts", 3);
		JsonArray targetIds = new JsonArray();
		JsonArray placements = new JsonArray();
		String status = "COMPLETE";
		String error = null;
		try {
			ServerLevel level = server.overworld();
			List<Holder.Reference<Structure>> targets = targets(server);
			for (Holder.Reference<Structure> target : targets) {
				targetIds.add(target.key().identifier().toString());
			}
			for (int index = 0; index < targets.size(); index++) {
				for (int attempt = 0; attempt < attempts; attempt++) {
					placements.add(place(server, level, targets.get(index), index, attempt));
					// The chunk side of a server tick: expires the tickets that loading this placement
					// added and unloads its chunks. Without it the chunks stay resident and the heap runs out.
					level.getChunkSource().tick(() -> true, false);
				}
			}
		} catch (Throwable t) {
			status = "ERROR";
			error = t.toString();
		}

		JsonObject summary = summarize(placements);
		JsonObject result = new JsonObject();
		result.addProperty("status", status);
		result.addProperty("attempts", attempts);
		result.add("targets", targetIds);
		result.add("placements", placements);
		result.add("summary", summary);
		if (error != null) {
			result.addProperty("error", error);
		}
		try {
			Files.writeString(Path.of(OUTPUT), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result) + "\n",
				StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println(MARKER + " ERROR could not write " + OUTPUT + ": " + e);
			return;
		}

		StringBuilder line = new StringBuilder(MARKER).append(' ').append(status)
			.append(" targets=").append(targetIds.size());
		for (var entry : summary.entrySet()) {
			line.append(' ').append(entry.getKey()).append('=').append(entry.getValue().getAsInt());
		}
		if (error != null) {
			line.append(" error=").append(error);
		}
		System.out.println(line);
	}

	/** Every structure in the registry whose id the probe targets, sorted by id. */
	private static List<Holder.Reference<Structure>> targets(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements()
			.filter(holder -> isTarget(holder.key().identifier()))
			.sorted(Comparator.comparing((Holder.Reference<Structure> holder) -> holder.key().identifier().toString()))
			.toList();
	}

	private static boolean isTarget(Identifier id) {
		String namespace = id.getNamespace();
		String path = id.getPath();
		return namespace.equals("structory_towers")
			|| (namespace.equals("nova_structures") && path.startsWith("shrine"))
			|| (namespace.equals("trek") && path.startsWith("village/"));
	}

	/** One placement, mirroring PlaceCommand.placeStructure, with the waystone findings recorded. */
	private static JsonObject place(MinecraftServer server, ServerLevel level, Holder.Reference<Structure> holder,
			int index, int attempt) {
		int x = SPACING * index;
		int z = SPACING * attempt;
		int y = 0;
		boolean valid = false;
		JsonElement boxJson = JsonNull.INSTANCE;
		int pieceCount = 0;
		JsonArray wayPieces = new JsonArray();
		JsonArray blocks = new JsonArray();
		String tp = null;
		String error = null;
		try {
			level.getChunk(x >> 4, z >> 4);
			y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos origin = new BlockPos(x, y, z);
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			StructureStart start = holder.value().generate(
				holder,
				level.dimension(),
				server.registryAccess(),
				generator,
				generator.getBiomeSource(),
				level.getChunkSource().randomState(),
				level.getStructureManager(),
				level.getSeed(),
				ChunkPos.containing(origin),
				0,
				level,
				biome -> true
			);
			valid = start.isValid();
			if (valid) {
				BoundingBox bounds = start.getBoundingBox();
				boxJson = box(bounds);
				pieceCount = start.getPieces().size();

				ChunkPos chunkMin = new ChunkPos(SectionPos.blockToSectionCoord(bounds.minX()), SectionPos.blockToSectionCoord(bounds.minZ()));
				ChunkPos chunkMax = new ChunkPos(SectionPos.blockToSectionCoord(bounds.maxX()), SectionPos.blockToSectionCoord(bounds.maxZ()));
				ChunkPos.rangeClosed(chunkMin, chunkMax).forEach(c -> level.getChunk(c.x(), c.z()));
				ChunkPos.rangeClosed(chunkMin, chunkMax).forEach(c -> start.placeInChunk(
					level,
					level.structureManager(),
					generator,
					level.getRandom(),
					new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()),
					c
				));

				BlockPos firstPieceCenter = null;
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof PoolElementStructurePiece pool) {
						String element = pool.getElement().toString();
						if (element.contains("waystone")) {
							JsonObject entry = new JsonObject();
							entry.addProperty("element", element);
							entry.add("bounding_box", box(pool.getBoundingBox()));
							wayPieces.add(entry);
							if (firstPieceCenter == null) {
								firstPieceCenter = pool.getBoundingBox().getCenter();
							}
						}
					}
				}

				int minY = Math.max(bounds.minY(), level.getMinY());
				int maxY = Math.min(bounds.maxY(), level.getMaxY());
				for (int bx = bounds.minX(); bx <= bounds.maxX(); bx++) {
					for (int by = minY; by <= maxY; by++) {
						for (int bz = bounds.minZ(); bz <= bounds.maxZ(); bz++) {
							BlockPos pos = new BlockPos(bx, by, bz);
							Identifier key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
							if (key != null && key.getNamespace().equals("waystones")) {
								JsonObject block = new JsonObject();
								block.add("pos", position(bx, by, bz));
								block.addProperty("block", key.toString());
								blocks.add(block);
							}
						}
					}
				}

				if (firstPieceCenter != null) {
					tp = "/tp @s " + firstPieceCenter.getX() + " " + (firstPieceCenter.getY() + 3) + " " + firstPieceCenter.getZ();
				} else {
					tp = "/tp @s " + ((bounds.minX() + bounds.maxX()) / 2) + " " + (bounds.maxY() + 1) + " "
						+ ((bounds.minZ() + bounds.maxZ()) / 2);
				}
			}
		} catch (Throwable t) {
			error = t.toString();
		}

		JsonObject record = new JsonObject();
		record.addProperty("structure", holder.key().identifier().toString());
		record.addProperty("attempt", attempt);
		record.add("origin", position(x, y, z));
		record.addProperty("valid", valid);
		record.add("bounding_box", boxJson);
		record.addProperty("piece_count", pieceCount);
		record.add("waystone_pieces", wayPieces);
		record.add("waystone_blocks", blocks);
		record.addProperty("tp", tp);
		if (error != null) {
			record.addProperty("error", error);
		}
		return record;
	}

	private static JsonObject summarize(JsonArray placements) {
		int valid = 0;
		int withBlocks = 0;
		int blockTotal = 0;
		int plaza = 0;
		for (JsonElement element : placements) {
			JsonObject placement = element.getAsJsonObject();
			if (placement.get("valid").getAsBoolean()) {
				valid++;
			}
			int blocks = placement.getAsJsonArray("waystone_blocks").size();
			if (blocks > 0) {
				withBlocks++;
			}
			blockTotal += blocks;
			for (JsonElement piece : placement.getAsJsonArray("waystone_pieces")) {
				if (piece.getAsJsonObject().get("element").getAsString().contains(PLAZA)) {
					plaza++;
				}
			}
		}
		JsonObject summary = new JsonObject();
		summary.addProperty("placements", placements.size());
		summary.addProperty("valid", valid);
		summary.addProperty("with_waystone_blocks", withBlocks);
		summary.addProperty("waystone_block_total", blockTotal);
		summary.addProperty("plaza_pieces", plaza);
		return summary;
	}

	private static JsonObject box(BoundingBox bounds) {
		JsonObject out = new JsonObject();
		out.add("min", position(bounds.minX(), bounds.minY(), bounds.minZ()));
		out.add("max", position(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
		return out;
	}

	private static JsonArray position(int x, int y, int z) {
		JsonArray out = new JsonArray();
		out.add(x);
		out.add(y);
		out.add(z);
		return out;
	}
}
