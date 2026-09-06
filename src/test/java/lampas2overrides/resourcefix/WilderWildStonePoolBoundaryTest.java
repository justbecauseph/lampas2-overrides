package lampas2overrides.resourcefix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Proves the repaired radius footprint stays within adjacent chunks at all chunk boundaries. */
public final class WilderWildStonePoolBoundaryTest {

	private static final int WRITE_RADIUS = 1;
	private static final int PATCH_SAMPLE_PLUS_ONE = 14 + 1;

	@Test
	void repairedMaximumRadiusStaysWithinWriteRadiusForPositiveAndNegativeChunkEdges() {
		for (int centerChunk : new int[] {-3, -1, 0, 2}) {
			for (int localX = 0; localX < 16; localX++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					int originX = centerChunk * 16 + localX;
					int originZ = centerChunk * 16 + localZ;
					assertFootprintWithinWriteRadius(centerChunk, centerChunk, originX, originZ,
						PATCH_SAMPLE_PLUS_ONE);
				}
			}
		}
	}

	@Test
	void oldMaximumRadiusWouldReachChunkDistanceTwoAtTheCorner() {
		int oldRadius = 15 + 1;
		int originX = 15;
		int originZ = 15;
		assertTrue(Math.floorDiv(originX + oldRadius + 1, 16) - Math.floorDiv(originX, 16) > WRITE_RADIUS);
		assertTrue(Math.floorDiv(originZ + oldRadius + 1, 16) - Math.floorDiv(originZ, 16) > WRITE_RADIUS);
	}

	@Test
	void radiusExtremesAreExplicitlyBounded() {
		int minimumConfiguredRadius = 12;
		int maximumConfiguredRadius = 14;
		int sampledMaximumPlusOne = maximumConfiguredRadius + 1;
		int exposureNeighborMargin = 1;
		assertTrue(minimumConfiguredRadius < maximumConfiguredRadius);
		assertEquals(15, sampledMaximumPlusOne);
		assertTrue(sampledMaximumPlusOne + exposureNeighborMargin <= 16,
			"sampled radius plus FrozenLib's exposure margin must fit the 16-block chunk span");
	}

	private static void assertFootprintWithinWriteRadius(int centerChunkX, int centerChunkZ,
			int originX, int originZ, int radius) {
		Set<Long> touchedChunks = new HashSet<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				addChunk(touchedChunks, originX + dx, originZ + dz);
				addChunk(touchedChunks, originX + dx + 1, originZ + dz);
				addChunk(touchedChunks, originX + dx - 1, originZ + dz);
				addChunk(touchedChunks, originX + dx, originZ + dz + 1);
				addChunk(touchedChunks, originX + dx, originZ + dz - 1);
			}
		}
		for (long packed : touchedChunks) {
			int chunkX = (int) (packed >> 32);
			int chunkZ = (int) packed;
			assertTrue(Math.abs(chunkX - centerChunkX) <= WRITE_RADIUS,
				"x chunk escaped write radius: " + chunkX);
			assertTrue(Math.abs(chunkZ - centerChunkZ) <= WRITE_RADIUS,
				"z chunk escaped write radius: " + chunkZ);
		}
	}

	private static void addChunk(Set<Long> touchedChunks, int x, int z) {
		int chunkX = Math.floorDiv(x, 16);
		int chunkZ = Math.floorDiv(z, 16);
		touchedChunks.add(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
	}
}
