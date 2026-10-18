package lampas2overrides.crowdcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import lampas2overrides.crowdcontrol.ApothicBossMarkers.Marker;

/**
 * The marker rule on Placebo data tags built here. The tags mirror what Apotheosis 10.0.4 writes: {@code Invader.initBoss}
 * puts the boolean {@code apoth.boss} (and {@code apoth.boss.rarity}), {@code Elite.initElite} puts the boolean
 * {@code apoth.miniboss}, and {@code trySpawnElite} / {@code EliteWaveEntity} put the elite id as a string under the same key
 * while the elite is pending. No Placebo or Apotheosis code runs here.
 */
class ApothicBossMarkersTest {

	@Test
	void noDataIsUnmarked() {
		assertEquals(Marker.NONE, ApothicBossMarkers.markerOf((CompoundTag) null));
		assertEquals(Marker.NONE, ApothicBossMarkers.markerOf(new CompoundTag()));
	}

	@Test
	void invaderFlagMarksAnInvader() {
		CompoundTag data = new CompoundTag();
		data.putBoolean("apoth.boss", true);
		data.putString("apoth.boss.rarity", "apotheosis:epic");

		assertEquals(Marker.INVADER, ApothicBossMarkers.markerOf(data));
	}

	@Test
	void rarityAloneIsNotAnInvader() {
		CompoundTag data = new CompoundTag();
		data.putString("apoth.boss.rarity", "apotheosis:epic");

		assertEquals(Marker.NONE, ApothicBossMarkers.markerOf(data));
	}

	@Test
	void transformedEliteIsMarked() {
		CompoundTag data = new CompoundTag();
		data.putBoolean("apoth.miniboss", true);

		assertEquals(Marker.ELITE, ApothicBossMarkers.markerOf(data));
	}

	@Test
	void pendingEliteIsNotMarked() {
		CompoundTag data = new CompoundTag();
		data.putString("apoth.miniboss", "apotheosis:overworld/craig");
		data.putString("apoth.miniboss.player", "8cd8ddea-9067-4bf8-b41b-5e06858bda6e");

		assertEquals(Marker.NONE, ApothicBossMarkers.markerOf(data));
	}

	@Test
	void eliteFlagOfFalseIsNotMarked() {
		CompoundTag data = new CompoundTag();
		data.putBoolean("apoth.miniboss", false);

		assertEquals(Marker.NONE, ApothicBossMarkers.markerOf(data));
	}
}
