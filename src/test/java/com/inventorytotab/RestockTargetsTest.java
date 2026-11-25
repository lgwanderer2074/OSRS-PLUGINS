package com.inventorytotab;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RestockTargetsTest
{
	private static final int PRAYER_POTION_4 = 2434;
	private static final int PRAYER_POTION_3 = 139;
	private static final int ARROWS = 882;

	@Test
	public void distinguishesWithdrawableQuantitiesAndShortfallsByExactItem()
	{
		RestockTargets targets = new RestockTargets(emptyLayout(), Map.of(PRAYER_POTION_4, 6L), Map.of());
		List<RestockTargets.Need> needs = targets.calculate(Map.of(PRAYER_POTION_4, 2L), Map.of(),
			Map.of(PRAYER_POTION_4, 3L, PRAYER_POTION_3, 100L));

		assertEquals(1, needs.size());
		assertEquals(PRAYER_POTION_4, needs.get(0).itemId);
		assertEquals(3L, needs.get(0).withdraw);
		assertEquals(1L, needs.get(0).shortfall);
	}

	@Test
	public void countsWornItemsOnlyTowardTheirSavedEquipmentQuantity()
	{
		RestockTargets targets = new RestockTargets(emptyLayout(), Map.of(ARROWS, 100L), Map.of(ARROWS, 50L));
		Map<Integer, Long> worn = Map.of(ARROWS, 200L);
		Map<Integer, Long> bank = Map.of(ARROWS, 500L);

		assertEquals(80L, targets.calculate(Map.of(ARROWS, 20L), worn, bank).get(0).withdraw);
		assertTrue(targets.calculate(Map.of(ARROWS, 100L), worn, bank).isEmpty());
	}

	@Test
	public void toleratesNewBankItemsInGapsButRejectsAChangedSnapshot()
	{
		int[] layout = emptyLayout();
		layout[4] = PRAYER_POTION_4;
		RestockTargets targets = new RestockTargets(layout, Map.of(PRAYER_POTION_4, 6L), Map.of());
		int[] reopenedLayout = layout.clone();
		reopenedLayout[5] = ARROWS;

		assertTrue(targets.matches(reopenedLayout));
		reopenedLayout[4] = PRAYER_POTION_3;
		assertFalse(targets.matches(reopenedLayout));
	}

	@Test
	public void matchesASetupSavedLowerInTheTabAtItsOffset()
	{
		int[] block = emptyLayout();
		block[0] = PRAYER_POTION_4;
		RestockTargets lower = new RestockTargets(block, 64, Map.of(PRAYER_POTION_4, 1L), Map.of());
		int[] tab = new int[64 + InventoryLayoutBuilder.SNAPSHOT_SIZE];
		Arrays.fill(tab, -1);
		tab[64] = PRAYER_POTION_4;

		assertTrue(lower.matches(tab));
		tab[64] = ARROWS;
		assertFalse(lower.matches(tab));
	}

	@Test
	public void similarityPrefersTheSetupClosestToWhatThePlayerHolds()
	{
		RestockTargets potions = new RestockTargets(emptyLayout(), Map.of(PRAYER_POTION_4, 6L), Map.of());
		RestockTargets arrows = new RestockTargets(emptyLayout(), Map.of(ARROWS, 100L), Map.of());
		Map<Integer, Long> held = Map.of(PRAYER_POTION_4, 2L);

		assertTrue(potions.similarity(held) > arrows.similarity(held));
		assertEquals(0.0, arrows.similarity(held), 0.0);
		assertEquals(1.0, potions.similarity(Map.of(PRAYER_POTION_4, 6L)), 0.0);
	}

	@Test
	public void savesAndLoadsSeveralSetupsAndReadsOlderSingleSetupRecords()
	{
		Gson gson = new Gson();
		RestockTargets first = new RestockTargets(emptyLayout(), Map.of(PRAYER_POTION_4, 6L), Map.of());
		RestockTargets second = new RestockTargets(emptyLayout(), 64, Map.of(ARROWS, 100L), Map.of(ARROWS, 50L));

		List<RestockTargets> loaded = RestockTargets.parse(gson, RestockTargets.write(gson, List.of(first, second)));
		assertEquals(2, loaded.size());
		assertEquals(0, loaded.get(0).offset());
		assertEquals(64, loaded.get(1).offset());
		assertTrue(loaded.get(1).includes(ARROWS));

		List<RestockTargets> legacy = RestockTargets.parse(gson, gson.toJson(first));
		assertEquals(1, legacy.size());
		assertEquals(0, legacy.get(0).offset());
		assertTrue(legacy.get(0).includes(PRAYER_POTION_4));

		assertTrue(RestockTargets.parse(gson, null).isEmpty());
		assertTrue(RestockTargets.parse(gson, "not json {").isEmpty());
	}

	private static int[] emptyLayout()
	{
		int[] layout = new int[InventoryLayoutBuilder.SNAPSHOT_SIZE];
		Arrays.fill(layout, -1);
		return layout;
	}
}
