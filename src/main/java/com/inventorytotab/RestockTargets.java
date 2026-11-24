package com.inventorytotab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RestockTargets
{
	private final int[] layout;
	private final Map<Integer, Long> inventory;
	private final Map<Integer, Long> equipment;

	RestockTargets(int[] layout, Map<Integer, Long> inventory, Map<Integer, Long> equipment)
	{
		this.layout = Arrays.copyOf(layout, InventoryLayoutBuilder.SNAPSHOT_SIZE);
		this.inventory = inventory;
		this.equipment = equipment;
	}

	boolean matches(int[] currentLayout)
	{
		if (layout == null || layout.length != InventoryLayoutBuilder.SNAPSHOT_SIZE
			|| inventory == null || equipment == null || currentLayout == null)
		{
			return false;
		}
		for (int slot = 0; slot < layout.length; slot++)
		{
			// Bank Tags can add other items to empty cells. Check only the saved items.
			if (layout[slot] >= 0 && (slot >= currentLayout.length || layout[slot] != currentLayout[slot]))
			{
				return false;
			}
		}
		return true;
	}

	boolean includes(int itemId)
	{
		return inventory.containsKey(itemId) || equipment.containsKey(itemId);
	}

	List<Need> calculate(Map<Integer, Long> carried, Map<Integer, Long> worn, Map<Integer, Long> bank)
	{
		Map<Integer, Long> totals = new LinkedHashMap<>(inventory);
		equipment.forEach((itemId, quantity) -> totals.merge(itemId, quantity, Long::sum));

		List<Need> needs = new ArrayList<>();
		totals.forEach((itemId, quantity) ->
		{
			long equipped = Math.min(worn.getOrDefault(itemId, 0L), equipment.getOrDefault(itemId, 0L));
			long missing = quantity - carried.getOrDefault(itemId, 0L) - equipped;
			if (missing > 0)
			{
				long available = Math.min(missing, bank.getOrDefault(itemId, 0L));
				needs.add(new Need(itemId, available, missing - available));
			}
		});
		return needs;
	}

	static final class Need
	{
		final int itemId;
		final long withdraw;
		final long shortfall;

		Need(int itemId, long withdraw, long shortfall)
		{
			this.itemId = itemId;
			this.withdraw = withdraw;
			this.shortfall = shortfall;
		}
	}
}
