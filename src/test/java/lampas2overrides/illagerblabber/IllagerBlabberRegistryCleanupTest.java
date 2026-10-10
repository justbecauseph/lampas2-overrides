package lampas2overrides.illagerblabber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IllagerBlabberRegistryCleanupTest {

	private static final UUID ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
	private static final UUID OTHER = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
	private static final Function<Object, Object> MANAGER_ENTITY = manager -> ((Manager) manager).entity();

	private Map<UUID, Object> managers;
	private List<Map<UUID, ?>> perEntity;

	@BeforeEach
	void setUp() {
		managers = new ConcurrentHashMap<>();
		Map<UUID, Object> hadTargetLastTick = new ConcurrentHashMap<>();
		Map<UUID, Object> victoryTimers = new ConcurrentHashMap<>();
		Map<UUID, Object> combatDebounceTimers = new ConcurrentHashMap<>();
		Map<UUID, Object> lastProcessedTick = new ConcurrentHashMap<>();
		Map<UUID, Object> lastPillagerTargets = new ConcurrentHashMap<>();
		Map<UUID, Object> lastVindicatorTargets = new ConcurrentHashMap<>();
		Map<UUID, Object> lastEvokerTargets = new ConcurrentHashMap<>();
		perEntity = List.<Map<UUID, ?>>of(managers, hadTargetLastTick, victoryTimers, combatDebounceTimers,
			lastProcessedTick, lastPillagerTargets, lastVindicatorTargets, lastEvokerTargets);
	}

	@Test
	void ownedUnloadClearsEveryPerEntityMapForThatIdOnly() {
		Entity entity = new Entity();
		fillState(ID);
		fillState(OTHER);
		managers.put(ID, new Manager(entity));

		assertTrue(IllagerBlabberRegistryCleanup.forgetIfOwned(ID, entity, managers, MANAGER_ENTITY, perEntity));

		for (Map<UUID, ?> map : perEntity) {
			assertFalse(map.containsKey(ID), "owned unload must clear the UUID from every map");
			assertTrue(map.containsKey(OTHER), "unrelated UUIDs must survive");
		}
	}

	@Test
	void newerInstanceManagerSurvivesOlderInstanceUnload() {
		Entity older = new Entity();
		Entity newer = new Entity();
		fillState(ID);
		managers.put(ID, new Manager(newer));

		assertFalse(IllagerBlabberRegistryCleanup.forgetIfOwned(ID, older, managers, MANAGER_ENTITY, perEntity));

		for (Map<UUID, ?> map : perEntity) {
			assertTrue(map.containsKey(ID), "a manager owned by a newer instance must be left intact");
		}
		assertEquals(newer, ((Manager) managers.get(ID)).entity());
	}

	@Test
	void ownershipIsIdentityNotEquality() {
		EqualEntity first = new EqualEntity();
		EqualEntity second = new EqualEntity();
		assertEquals(first, second);
		fillState(ID);
		managers.put(ID, new Manager(first));

		assertFalse(IllagerBlabberRegistryCleanup.forgetIfOwned(ID, second, managers, MANAGER_ENTITY, perEntity));
		assertTrue(managers.containsKey(ID));
	}

	@Test
	void absentManagerStillClearsPerEntityState() {
		fillState(ID);
		managers.remove(ID);

		assertTrue(IllagerBlabberRegistryCleanup.forgetIfOwned(ID, new Entity(), managers, MANAGER_ENTITY, perEntity));

		for (Map<UUID, ?> map : perEntity) {
			assertFalse(map.containsKey(ID));
		}
	}

	@Test
	void forgetRemovesOnlyTheRequestedIdentifier() {
		fillState(ID);
		fillState(OTHER);

		IllagerBlabberRegistryCleanup.forget(ID, perEntity);

		for (Map<UUID, ?> map : perEntity) {
			assertFalse(map.containsKey(ID));
			assertTrue(map.containsKey(OTHER));
		}
	}

	@Test
	void clearAllEmptiesEveryMap() {
		fillState(ID);
		fillState(OTHER);

		IllagerBlabberRegistryCleanup.clearAll(perEntity);

		for (Map<UUID, ?> map : perEntity) {
			assertTrue(map.isEmpty());
		}
	}

	private void fillState(UUID id) {
		for (Map<UUID, ?> map : perEntity) {
			@SuppressWarnings("unchecked")
			Map<UUID, Object> writable = (Map<UUID, Object>) map;
			writable.put(id, "state");
		}
	}

	private record Manager(Object entity) {
	}

	private static final class Entity {
	}

	private static final class EqualEntity {
		@Override
		public boolean equals(Object other) {
			return other instanceof EqualEntity;
		}

		@Override
		public int hashCode() {
			return 1;
		}
	}
}
