package com.inventorytotab;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class InventoryLayoutBuilderTest
{
	@Test
	public void copiesGearAndInventorySideBySideAndKeepsOtherItemsBelow()
	{
		int[] inventory = emptyInventory();
		inventory[0] = 385;
		inventory[2] = 385;
		inventory[27] = 4151;
		int[] equipment = {
			-1, 1153, -1,
			-1, -1, 882,
			4151, 1115, -1,
			-1, 1067, -1,
			-1, -1, 2550
		};

		assertArrayEquals(new int[]
		{
			-1, 1153, -1, -1, 385, -1, 385, -1,
			-1, -1, 882, -1, -1, -1, -1, -1,
			4151, 1115, -1, -1, -1, -1, -1, -1,
			-1, 1067, -1, -1, -1, -1, -1, -1,
			-1, -1, 2550, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, 4151,
			995, 11840
		}, InventoryLayoutBuilder.build(inventory, equipment, new int[]{1153, 995, 4151},
			new int[]{385, 1115, 11840, 995}));
	}

	@Test
	public void copiesAllSevenInventoryRowsIntoLeftHalfOfBank()
	{
		int[] inventory = new int[28];
		for (int slot = 0; slot < inventory.length; slot++)
		{
			inventory[slot] = 100 + slot;
		}

		assertArrayEquals(new int[]
		{
			100, 101, 102, 103, -1, -1, -1, -1,
			104, 105, 106, 107, -1, -1, -1, -1,
			108, 109, 110, 111, -1, -1, -1, -1,
			112, 113, 114, 115, -1, -1, -1, -1,
			116, 117, 118, 119, -1, -1, -1, -1,
			120, 121, 122, 123, -1, -1, -1, -1,
			124, 125, 126, 127, -1, -1, -1, -1
		}, InventoryLayoutBuilder.build(inventory, null, null, null));
	}

	@Test
	public void preservesInventoryGapsAndRepeatedItems()
	{
		int[] inventory = emptyInventory();
		inventory[0] = 4151;
		inventory[2] = 385;
		inventory[3] = 385;
		inventory[25] = 3024;
		inventory[27] = 385;

		assertArrayEquals(new int[]
		{
			4151, -1, 385, 385, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			-1, 3024, -1, 385, -1, -1, -1, -1
		}, InventoryLayoutBuilder.build(inventory, null, null, null));
	}

	@Test
	public void preservesOtherItemsOnceInStableOrderBelowInventory()
	{
		int[] inventory = emptyInventory();
		inventory[0] = 385;
		inventory[1] = 385;
		inventory[4] = 3024;

		int[] previous = {-1, 4151, 385, 1127, 4151, -1, 3024, 995};
		int[] tagged = {995, 385, 11840, -1, 1127, 11840, 0};
		int[] layout = InventoryLayoutBuilder.build(inventory, null, previous, tagged);

		assertEquals(61, layout.length);
		assertArrayEquals(new int[]{4151, 1127, 995, 11840, 0},
			Arrays.copyOfRange(layout, 56, layout.length));
		assertEquals(385, layout[0]);
		assertEquals(385, layout[1]);
		assertEquals(3024, layout[8]);
		assertEquals(-1, layout[55]);
	}

	@Test
	public void leavesInputArraysUnchanged()
	{
		int[] inventory = emptyInventory();
		inventory[27] = 385;
		int[] previous = {4151, 385, -1, 4151};
		int[] tagged = {995, -1, 4151};
		int[] inventoryBefore = inventory.clone();
		int[] previousBefore = previous.clone();
		int[] taggedBefore = tagged.clone();

		int[] result = InventoryLayoutBuilder.build(inventory, null, previous, tagged);
		result[0] = 12345;
		result[56] = 12345;

		assertArrayEquals(inventoryBefore, inventory);
		assertArrayEquals(previousBefore, previous);
		assertArrayEquals(taggedBefore, tagged);
	}

	@Test
	public void repeatedSavesDoNotGrowLayoutOrAccumulateDuplicates()
	{
		int[] inventory = emptyInventory();
		inventory[3] = 385;
		inventory[27] = 3024;
		int[] tagged = {385, 4151, 995, 4151};
		int[] first = InventoryLayoutBuilder.build(inventory, null, new int[]{995, 4151, 995}, tagged);
		int[] second = InventoryLayoutBuilder.build(inventory, null, first, tagged);
		int[] third = InventoryLayoutBuilder.build(inventory, null, second, tagged);

		assertEquals(58, first.length);
		assertArrayEquals(first, second);
		assertArrayEquals(first, third);
	}

	@Test
	public void emptyInventoryStillReservesSevenRows()
	{
		int[] expected = new int[56];
		Arrays.fill(expected, -1);
		assertArrayEquals(expected, InventoryLayoutBuilder.build(emptyInventory(), null, new int[0], new int[0]));
	}

	@Test
	public void emptyInventoryRetainsAllOtherItemsBelowReservedRows()
	{
		int[] layout = InventoryLayoutBuilder.build(emptyInventory(), null, new int[]{385, -1, 385}, new int[]{995});
		assertEquals(58, layout.length);
		assertArrayEquals(new int[]{385, 995}, Arrays.copyOfRange(layout, 56, layout.length));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInventoryWithTooFewSlots()
	{
		InventoryLayoutBuilder.build(new int[27], null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInventoryWithTooManySlots()
	{
		InventoryLayoutBuilder.build(new int[29], null, null, null);
	}

	@Test
	public void appendKeepsExistingLayoutAndAddsNewSetupBelowAGapRow()
	{
		int[] first = emptyInventory();
		first[0] = 385;
		int[] existing = InventoryLayoutBuilder.build(first, null, null, null);
		int[] second = emptyInventory();
		second[1] = 2434;

		InventoryLayoutBuilder.Result result = InventoryLayoutBuilder.append(second, null, existing, new int[]{385, 2434});

		// The first setup only uses row 0, so the new block starts at row 2.
		assertEquals(16, result.offset);
		assertArrayEquals(Arrays.copyOf(existing, 16), Arrays.copyOf(result.layout, 16));
		assertEquals(2434, result.layout[17]);
		assertEquals(16 + InventoryLayoutBuilder.SNAPSHOT_SIZE, result.layout.length);
	}

	@Test
	public void appendStacksBelowTheLastUsedRowIncludingGearAndExtras()
	{
		int[] inventory = emptyInventory();
		inventory[27] = 4151;
		int[] existing = InventoryLayoutBuilder.build(inventory, null, new int[]{995}, null);

		InventoryLayoutBuilder.Result result = InventoryLayoutBuilder.append(emptyInventory(),
			new int[]{-1, 1153, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, existing, new int[]{11840});

		// Existing items end at cell 56 (row 7), so the new block starts at row 9.
		assertEquals(72, result.offset);
		assertArrayEquals(existing, Arrays.copyOf(result.layout, existing.length));
		assertEquals(1153, result.layout[73]);
		// Tagged items not placed anywhere follow the new block.
		assertEquals(11840, result.layout[result.layout.length - 1]);
	}

	@Test
	public void appendIntoEmptyLayoutStartsAtTheTop()
	{
		int[] inventory = emptyInventory();
		inventory[0] = 385;
		InventoryLayoutBuilder.Result result = InventoryLayoutBuilder.append(inventory, null, new int[]{-1, -1}, null);
		assertEquals(0, result.offset);
		assertEquals(385, result.layout[0]);
	}

	private static int[] emptyInventory()
	{
		int[] inventory = new int[28];
		Arrays.fill(inventory, -1);
		return inventory;
	}
}
