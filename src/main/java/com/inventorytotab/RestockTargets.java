package com.inventorytotab;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One saved setup: its seven-row block, where that block starts in the tab, and its quantities. */
final class RestockTargets
{
	private final int[] layout;
	private final int offset;
	private final Map<Integer, Long> inventory;
	private final Map<Integer, Long> equipment;

	RestockTargets(int[] layout, Map<Integer, Long> inventory, Map<Integer, Long> equipment)
	{
		this(layout, 0, inventory, equipment);
	}

	RestockTargets(int[] layout, int offset, Map<Integer, Long> inventory, Map<Integer, Long> equipment)
	{
		this.layout = Arrays.copyOf(layout, InventoryLayoutBuilder.SNAPSHOT_SIZE);
		this.offset = offset;
		this.inventory = inventory;
		this.equipment = equipment;
	}

	/** Every setup saved for a tab, oldest first. */
	static final class Saved
	{
		final List<RestockTargets> setups;

		Saved(List<RestockTargets> setups)
		{
			this.setups = setups;
		}
	}

	/**
	 * Reads a tab's saved setups. Records written before multiple setups existed hold a
	 * single setup at the top of the tab; they load as a one-setup list.
	 */
	static List<RestockTargets> parse(Gson gson, String raw)
	{
		if (raw == null)
		{
			return List.of();
		}
		try
		{
			JsonElement json = gson.fromJson(raw, JsonElement.class);
			if (json == null || !json.isJsonObject())
			{
				return List.of();
			}
			JsonObject object = json.getAsJsonObject();
			List<RestockTargets> setups = new ArrayList<>();
			if (object.has("setups"))
			{
				Saved saved = gson.fromJson(object, Saved.class);
				if (saved.setups != null)
				{
					setups.addAll(saved.setups);
				}
			}
			else
			{
				setups.add(gson.fromJson(object, RestockTargets.class));
			}
			setups.removeIf(setup -> setup == null || !setup.isValid());
			return setups;
		}
		catch (JsonParseException ex)
		{
			return List.of();
		}
	}

	static String write(Gson gson, List<RestockTargets> setups)
	{
		return gson.toJson(new Saved(setups));
	}

	private boolean isValid()
	{
		return layout != null && layout.length == InventoryLayoutBuilder.SNAPSHOT_SIZE && offset >= 0
			&& inventory != null && equipment != null;
	}

	int offset()
	{
		return offset;
	}

	boolean matches(int[] currentLayout)
	{
		if (!isValid() || currentLayout == null)
		{
			return false;
		}
		for (int slot = 0; slot < layout.length; slot++)
		{
			// Bank Tags can add other items to empty cells. Check only the saved items.
			int position = offset + slot;
			if (layout[slot] >= 0 && (position >= currentLayout.length || layout[slot] != currentLayout[position]))
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

	private Map<Integer, Long> totals()
	{
		Map<Integer, Long> totals = new LinkedHashMap<>(inventory);
		equipment.forEach((itemId, quantity) -> totals.merge(itemId, quantity, Long::sum));
		return totals;
	}

	/**
	 * How closely the player's carried and worn items resemble this setup, from 0 to 1:
	 * shared quantity divided by combined quantity, so both missing and extra items lower it.
	 */
	double similarity(Map<Integer, Long> held)
	{
		Map<Integer, Long> totals = totals();
		Set<Integer> items = new HashSet<>(totals.keySet());
		items.addAll(held.keySet());
		long shared = 0;
		long combined = 0;
		for (int itemId : items)
		{
			long target = totals.getOrDefault(itemId, 0L);
			long have = held.getOrDefault(itemId, 0L);
			shared += Math.min(target, have);
			combined += Math.max(target, have);
		}
		return combined == 0 ? 0 : (double) shared / combined;
	}

	List<Need> calculate(Map<Integer, Long> carried, Map<Integer, Long> worn, Map<Integer, Long> bank)
	{
		List<Need> needs = new ArrayList<>();
		totals().forEach((itemId, quantity) ->
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
