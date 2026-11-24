package com.inventorytotab;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class InventoryLayoutBuilder
{
	private static final int INVENTORY_SIZE = 28;
	private static final int INVENTORY_COLUMNS = 4;
	private static final int EQUIPMENT_COLUMNS = 3;
	private static final int BANK_COLUMNS = 8;
	static final int SNAPSHOT_SIZE = 56;
	private static final int EMPTY_ITEM = -1;

	private InventoryLayoutBuilder()
	{
	}

	/**
	 * Uses seven bank rows for the snapshot. With equipment, the inventory moves
	 * to the right and the equipment panel occupies the left three columns.
	 * Existing items outside the snapshot follow below in their previous order.
	 * Previous layouts and tagged item lists may be null when no data is saved.
	 */
	static int[] build(int[] inventory28, int[] equipmentPanel, int[] previousLayout, int[] existingTaggedItems)
	{
		Objects.requireNonNull(inventory28, "inventory28");
		if (inventory28.length != INVENTORY_SIZE)
		{
			throw new IllegalArgumentException("Inventory must contain exactly 28 slots");
		}

		int[] layout = new int[SNAPSHOT_SIZE];
		Arrays.fill(layout, EMPTY_ITEM);
		int inventoryOffset = equipmentPanel == null ? 0 : INVENTORY_COLUMNS;
		for (int slot = 0; slot < INVENTORY_SIZE; slot++)
		{
			int position = (slot / INVENTORY_COLUMNS) * BANK_COLUMNS + slot % INVENTORY_COLUMNS;
			layout[position + inventoryOffset] = inventory28[slot];
		}
		if (equipmentPanel != null)
		{
			for (int slot = 0; slot < equipmentPanel.length; slot++)
			{
				int position = (slot / EQUIPMENT_COLUMNS) * BANK_COLUMNS + slot % EQUIPMENT_COLUMNS;
				layout[position] = equipmentPanel[slot];
			}
		}

		Set<Integer> snapshotItems = new LinkedHashSet<>();
		for (int itemId : layout)
		{
			if (itemId >= 0)
			{
				snapshotItems.add(itemId);
			}
		}

		Set<Integer> extras = new LinkedHashSet<>();
		addExtras(previousLayout, snapshotItems, extras);
		addExtras(existingTaggedItems, snapshotItems, extras);

		layout = Arrays.copyOf(layout, SNAPSHOT_SIZE + extras.size());
		int position = SNAPSHOT_SIZE;
		for (int itemId : extras)
		{
			layout[position++] = itemId;
		}
		return layout;
	}

	private static void addExtras(int[] items, Set<Integer> snapshotItems, Set<Integer> extras)
	{
		if (items == null)
		{
			return;
		}
		for (int itemId : items)
		{
			if (itemId >= 0 && !snapshotItems.contains(itemId))
			{
				extras.add(itemId);
			}
		}
	}
}
