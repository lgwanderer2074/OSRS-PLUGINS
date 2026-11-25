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
	static final int BANK_COLUMNS = 8;
	static final int SNAPSHOT_SIZE = 56;
	private static final int EMPTY_ITEM = -1;

	private InventoryLayoutBuilder()
	{
	}

	/** A built tab layout plus the cell where the new setup's block starts. */
	static final class Result
	{
		final int[] layout;
		final int offset;

		private Result(int[] layout, int offset)
		{
			this.layout = layout;
			this.offset = offset;
		}
	}

	/**
	 * Builds the seven-row block for one setup. With equipment, the inventory moves
	 * to the right and the equipment panel occupies the left three columns.
	 */
	static int[] block(int[] inventory28, int[] equipmentPanel)
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
		return layout;
	}

	/**
	 * Replaces the tab with one setup at the top. Existing items outside the new block
	 * follow below in their previous order. Previous layouts and tagged item lists may
	 * be null when no data is saved.
	 */
	static int[] build(int[] inventory28, int[] equipmentPanel, int[] previousLayout, int[] existingTaggedItems)
	{
		return withExtras(block(inventory28, equipmentPanel), previousLayout, existingTaggedItems);
	}

	/**
	 * Keeps the previous layout exactly as it is and adds the new setup underneath,
	 * leaving one empty row between them. Tagged items that are not placed anywhere
	 * go after the new block so Bank Tags does not drop them into its empty slots.
	 */
	static Result append(int[] inventory28, int[] equipmentPanel, int[] previousLayout, int[] existingTaggedItems)
	{
		int[] newBlock = block(inventory28, equipmentPanel);
		int[] previous = previousLayout == null ? new int[0] : previousLayout;
		int lastUsed = -1;
		for (int slot = 0; slot < previous.length; slot++)
		{
			if (previous[slot] >= 0)
			{
				lastUsed = slot;
			}
		}
		int offset = lastUsed < 0 ? 0 : (lastUsed / BANK_COLUMNS + 2) * BANK_COLUMNS;

		int[] combined = new int[offset + SNAPSHOT_SIZE];
		Arrays.fill(combined, EMPTY_ITEM);
		System.arraycopy(previous, 0, combined, 0, Math.min(previous.length, offset));
		System.arraycopy(newBlock, 0, combined, offset, SNAPSHOT_SIZE);
		return new Result(withExtras(combined, null, existingTaggedItems), offset);
	}

	private static int[] withExtras(int[] layout, int[] previousLayout, int[] existingTaggedItems)
	{
		Set<Integer> placed = new LinkedHashSet<>();
		for (int itemId : layout)
		{
			if (itemId >= 0)
			{
				placed.add(itemId);
			}
		}

		Set<Integer> extras = new LinkedHashSet<>();
		addExtras(previousLayout, placed, extras);
		addExtras(existingTaggedItems, placed, extras);

		int[] result = Arrays.copyOf(layout, layout.length + extras.size());
		int position = layout.length;
		for (int itemId : extras)
		{
			result[position++] = itemId;
		}
		return result;
	}

	private static void addExtras(int[] items, Set<Integer> placed, Set<Integer> extras)
	{
		if (items == null)
		{
			return;
		}
		for (int itemId : items)
		{
			if (itemId >= 0 && !placed.contains(itemId))
			{
				extras.add(itemId);
			}
		}
	}
}
