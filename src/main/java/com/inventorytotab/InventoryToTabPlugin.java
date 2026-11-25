package com.inventorytotab;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Inventory to Tab",
	description = "Save your inventory and gear to a Bank Tag tab and see what to restock",
	tags = {"bank", "inventory", "layout", "snapshot"}
)
@PluginDependency(BankTagsPlugin.class)
public class InventoryToTabPlugin extends Plugin
{
	private static final String SAVE_OPTION = "Save inventory here";
	private static final String SAVE_GEAR_OPTION = "Save gear and inventory here";
	private static final String UNDO_OPTION = "Undo last setup save";
	private static final String SHOW_RESTOCK_OPTION = "Show restock list";
	private static final String HIDE_RESTOCK_OPTION = "Hide restock list";
	private static final String CONFIG_GROUP = "inventory-to-tab";
	private static final String RESTOCK_HIDDEN_KEY = "restockHidden";
	private static final int INVENTORY_SIZE = 28;
	private static final EquipmentInventorySlot[] EQUIPMENT_PANEL = {
		null, EquipmentInventorySlot.HEAD, null,
		EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET, EquipmentInventorySlot.AMMO,
		EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD,
		null, EquipmentInventorySlot.LEGS, null,
		EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING
	};

	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private BankTagsService bankTags;
	@Inject private TagManager tagManager;
	@Inject private TabManager tabManager;
	@Inject private LayoutManager layoutManager;
	@Inject private ChatboxPanelManager chatbox;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private OverlayManager overlayManager;
	@Inject private RestockOverlay restockOverlay;
	@Inject private BankHighlightOverlay bankHighlightOverlay;

	private volatile long undoGeneration;
	private volatile LastSave lastSave;
	private volatile boolean restockDirty = true;
	private volatile boolean restockHidden;
	private String restockTag;

	@Override
	protected void startUp()
	{
		restockDirty = true;
		restockHidden = Boolean.TRUE.equals(
			configManager.getConfiguration(CONFIG_GROUP, RESTOCK_HIDDEN_KEY, Boolean.class));
		overlayManager.add(restockOverlay);
		overlayManager.add(bankHighlightOverlay);
	}

	@Override
	protected void shutDown()
	{
		resetSession();
		overlayManager.remove(restockOverlay);
		overlayManager.remove(bankHighlightOverlay);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Reuse the core tab's menu and let Bank Tags handle its widgets and withdrawals.
		if (event.getActionParam1() != InterfaceID.Bankmain.ITEMS_CONTAINER
			|| !"View tag tab".equals(event.getOption()))
		{
			return;
		}

		String tag = Text.standardize(event.getTarget());
		if (!isUserTab(tag))
		{
			return;
		}

		addSaveOption(tag, event.getTarget(), false);
		addSaveOption(tag, event.getTarget(), true);
		client.getMenu().createMenuEntry(-2)
			.setOption(restockHidden ? SHOW_RESTOCK_OPTION : HIDE_RESTOCK_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> toggleRestock(tag));
		LastSave previousSave = lastSave;
		if (previousSave != null && previousSave.tag.equals(tag))
		{
			client.getMenu().createMenuEntry(-2)
				.setOption(UNDO_OPTION)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> previousSave.undo.run());
		}
	}

	private void addSaveOption(String tag, String target, boolean includeGear)
	{
		client.getMenu().createMenuEntry(-2)
			.setOption(includeGear ? SAVE_GEAR_OPTION : SAVE_OPTION)
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> saveSnapshot(tag, includeGear));
	}

	private void toggleRestock(String tag)
	{
		restockHidden = !restockHidden;
		configManager.setConfiguration(CONFIG_GROUP, RESTOCK_HIDDEN_KEY, restockHidden);
		restockDirty = true;
		if (restockHidden)
		{
			clearRestock();
			message("Restock list and bank highlights hidden. Right-click a tab and choose '" + SHOW_RESTOCK_OPTION + "' to bring it back.");
			return;
		}

		// Explain when the list cannot appear, rather than showing nothing.
		if (!isBankOpen() || !tag.equals(bankTags.getActiveTag()))
		{
			message("Restock list on. Open the '" + tag + "' tab to see it.");
			return;
		}
		switch (restockStatus(tag, bankTags.getActiveLayout()))
		{
			case NOT_SAVED:
				message("Nothing saved in '" + tag + "' yet. Save your setup here to track restocking.");
				break;
			case LAYOUT_CHANGED:
				message("Items in '" + tag + "' were moved or removed since saving. Save again to track restocking.");
				break;
			default:
				message("Restock list on. Hold Alt and drag it to move it.");
				break;
		}
	}

	private enum RestockStatus
	{
		NOT_SAVED,
		LAYOUT_CHANGED,
		READY
	}

	private List<RestockTargets> loadSetups(String tag)
	{
		return RestockTargets.parse(gson, configManager.getRSProfileConfiguration(CONFIG_GROUP, restockKey(tag)));
	}

	/** Saved setups whose items are still where they were saved in the open tab, oldest first. */
	private List<RestockTargets> matchingSetups(List<RestockTargets> setups, Layout active)
	{
		List<RestockTargets> matching = new ArrayList<>();
		if (active != null)
		{
			for (RestockTargets setup : setups)
			{
				if (setup.matches(active.getLayout()))
				{
					matching.add(setup);
				}
			}
		}
		return matching;
	}

	private RestockStatus restockStatus(String tag, Layout active)
	{
		List<RestockTargets> setups = loadSetups(tag);
		if (setups.isEmpty())
		{
			return RestockStatus.NOT_SAVED;
		}
		return matchingSetups(setups, active).isEmpty() ? RestockStatus.LAYOUT_CHANGED : RestockStatus.READY;
	}

	private boolean isUserTab(String tag)
	{
		return !tag.isEmpty() && !"tagtabs".equals(tag)
			&& tabManager.find(tag) != null && !tagManager.isHidden(tag);
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		return client.getGameState() == GameState.LOGGED_IN && bank != null && !bank.isHidden();
	}

	private void saveSnapshot(String tag, boolean includeGear)
	{
		if (!isBankOpen() || !isUserTab(tag))
		{
			return;
		}
		if (!tag.equals(bankTags.getActiveTag()))
		{
			message("Open the '" + tag + "' tag tab before saving to it.");
			return;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		int[] slots = new int[INVENTORY_SIZE];
		Map<Integer, Long> inventoryCounts = new LinkedHashMap<>();
		Map<Integer, Long> equipmentCounts = new LinkedHashMap<>();
		for (int slot = 0; slot < INVENTORY_SIZE; ++slot)
		{
			slots[slot] = captureItem(inventory, slot, inventoryCounts);
		}

		int[] equipment;
		if (includeGear)
		{
			ItemContainer worn = client.getItemContainer(InventoryID.WORN);
			equipment = new int[EQUIPMENT_PANEL.length];
			for (int position = 0; position < EQUIPMENT_PANEL.length; ++position)
			{
				EquipmentInventorySlot slot = EQUIPMENT_PANEL[position];
				equipment[position] = slot == null ? -1 : captureItem(worn, slot.getSlotIdx(), equipmentCounts);
			}
		}
		else
		{
			equipment = null;
		}

		if (inventoryCounts.isEmpty() && equipmentCounts.isEmpty())
		{
			message(includeGear ? "Your inventory and equipment are empty." : "Your inventory is empty.");
			return;
		}

		Layout previous = layoutManager.loadLayout(tag);
		if (previous == null)
		{
			applySave(tag, null, false, slots, equipment, inventoryCounts, equipmentCounts);
			return;
		}

		TagTab tab = tabManager.find(tag);
		long generation = undoGeneration;
		String key = restockKey(tag);
		String targetsAtPrompt = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
		Runnable[] choice = new Runnable[2];
		for (int mode = 0; mode < 2; mode++)
		{
			boolean append = mode == 0;
			choice[mode] = () -> clientThread.invoke(() ->
			{
				Layout current = layoutManager.loadLayout(tag);
				if (!isCurrentTab(tag, generation) || tabManager.find(tag) != tab || current == null
					|| !Arrays.equals(previous.getLayout(), current.getLayout())
					|| !Objects.equals(targetsAtPrompt, configManager.getRSProfileConfiguration(CONFIG_GROUP, key)))
				{
					message("The tab changed. Open it and try saving again.");
					return;
				}
				applySave(tag, previous, append, slots, equipment, inventoryCounts, equipmentCounts);
			});
		}
		chatbox.openTextMenuInput("'" + tag + "' already has a layout.")
			.option("Add below (keep existing setups)", choice[0])
			.option("Replace all", choice[1])
			.option("Cancel", () -> { })
			.build();
	}

	/**
	 * Saves the snapshot into the tab. With append, the existing layout stays exactly as it
	 * was and the new setup goes underneath; otherwise the new setup takes the top of the tab
	 * and becomes its only tracked setup.
	 */
	private void applySave(String tag, Layout previous, boolean append, int[] slots, int[] equipment,
		Map<Integer, Long> inventoryCounts, Map<Integer, Long> equipmentCounts)
	{
		List<Integer> taggedItems = tagManager.getItemsForTag(tag);
		Set<Integer> membership = new HashSet<>(taggedItems);
		Set<Integer> visibleItems = new LinkedHashSet<>();
		for (int id : taggedItems)
		{
			if (id >= 0)
			{
				visibleItems.add(id);
			}
		}

		// Include all matching bank variants (e.g. several doses of a fuzzy-tagged potion),
		// so core Bank Tags does not insert them into the inventory's empty slots.
		BankTag filter = bankTags.getActiveBankTag();
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (filter != null && bank != null)
		{
			for (Item item : bank.getItems())
			{
				if (item.getId() >= 0 && filter.contains(item.getId()))
				{
					visibleItems.add(itemManager.canonicalize(item.getId()));
				}
			}
		}
		int[] existingItems = visibleItems.stream().mapToInt(Integer::intValue).toArray();
		int[] previousSlots = previous == null ? new int[0] : previous.getLayout();
		String key = restockKey(tag);
		String previousTargets = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
		List<RestockTargets> setups = new ArrayList<>();
		int[] newLayout;
		int offset;
		if (append)
		{
			InventoryLayoutBuilder.Result result = InventoryLayoutBuilder.append(slots, equipment, previousSlots, existingItems);
			newLayout = result.layout;
			offset = result.offset;
			// Earlier setups keep tracking as long as their items are still where they were saved.
			for (RestockTargets setup : RestockTargets.parse(gson, previousTargets))
			{
				if (setup.matches(newLayout))
				{
					setups.add(setup);
				}
			}
		}
		else
		{
			newLayout = InventoryLayoutBuilder.build(slots, equipment, previousSlots, existingItems);
			offset = 0;
		}
		int[] block = Arrays.copyOfRange(newLayout, offset, offset + InventoryLayoutBuilder.SNAPSHOT_SIZE);
		setups.add(new RestockTargets(block, offset, inventoryCounts, equipmentCounts));
		int setupNumber = setups.size();
		Layout saved = new Layout(tag, newLayout);
		int[] expectedLayout = saved.getLayout();
		String savedTargets = RestockTargets.write(gson, setups);

		Set<Integer> snapshotIds = new LinkedHashSet<>(inventoryCounts.keySet());
		snapshotIds.addAll(equipmentCounts.keySet());
		List<Integer> addedTags = new ArrayList<>();
		for (int id : snapshotIds)
		{
			// Respect existing variation tags rather than adding or removing them.
			if (!membership.contains(id) && !membership.contains(-ItemVariationMapping.map(id)))
			{
				addedTags.add(id);
				tagManager.addTag(id, tag, false);
			}
		}

		layoutManager.saveLayout(saved);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, key, savedTargets);
		bankTags.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS
			| BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM);

		long generation = ++undoGeneration;
		lastSave = new LastSave(tag, () -> undo(tag, previous, expectedLayout, addedTags, generation,
			key, previousTargets, savedTargets));
		restockDirty = true;
		message((setupNumber > 1 ? "Setup " + setupNumber + " added below in '" : "Setup saved to '")
			+ tag + "'. Right-click the tab to undo.");
	}

	private int captureItem(ItemContainer container, int slot, Map<Integer, Long> counts)
	{
		Item item = container == null ? null : container.getItem(slot);
		if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
		{
			return -1;
		}
		int id = itemManager.canonicalize(item.getId());
		counts.merge(id, (long) item.getQuantity(), Long::sum);
		return id;
	}

	private boolean isCurrentTab(String tag, long generation)
	{
		return generation == undoGeneration && isBankOpen() && tag.equals(bankTags.getActiveTag())
			&& isUserTab(tag);
	}

	private void undo(String tag, Layout previous, int[] expectedLayout, List<Integer> addedTags,
		long generation, String key, String previousTargets, String expectedTargets)
	{
		Layout current = layoutManager.loadLayout(tag);
		if (!isCurrentTab(tag, generation) || current == null
			|| !Arrays.equals(current.getLayout(), expectedLayout)
			|| !Objects.equals(configManager.getRSProfileConfiguration(CONFIG_GROUP, key), expectedTargets))
		{
			message("The account, profile or tab changed after saving. Undo was not applied.");
			return;
		}

		Set<Integer> membership = new HashSet<>(tagManager.getItemsForTag(tag));
		for (int id : addedTags)
		{
			// removeTag also removes variation tags; leave it alone if one was added since the save.
			if (!membership.contains(-ItemVariationMapping.map(id)))
			{
				tagManager.removeTag(id, tag);
			}
		}

		if (previous == null)
		{
			layoutManager.removeLayout(tag);
		}
		else
		{
			layoutManager.saveLayout(previous);
		}
		if (previousTargets == null)
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, key);
		}
		else
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key, previousTargets);
		}
		bankTags.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		resetSession();
		message("Save undone for '" + tag + "'.");
	}

	private String restockKey(String tag)
	{
		String encodedTag = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(tag.getBytes(StandardCharsets.UTF_8));
		return "snapshot_" + configManager.getProfile().getId() + "_" + encodedTag;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN || id == InventoryID.BANK)
		{
			restockDirty = true;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) || BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup()))
		{
			restockDirty = true;
		}
		LastSave previousSave = lastSave;
		if (previousSave != null && BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup())
			&& BankTagsPlugin.TAG_TABS_CONFIG.equals(event.getKey())
			&& (event.getNewValue() == null || !Text.fromCSV(event.getNewValue()).contains(previousSave.tag)))
		{
			lastSave = null;
			++undoGeneration;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		String tag = isBankOpen() ? bankTags.getActiveTag() : null;
		if (tag == null)
		{
			if (restockTag != null)
			{
				restockTag = null;
				clearRestock();
			}
			return;
		}
		if (!restockDirty && tag.equals(restockTag))
		{
			return;
		}
		restockDirty = false;
		restockTag = tag;
		Layout active = bankTags.getActiveLayout();
		if (restockHidden || active == null || !isUserTab(tag))
		{
			clearRestock();
			return;
		}

		List<RestockTargets> setups = loadSetups(tag);
		List<RestockTargets> matching = matchingSetups(setups, active);
		if (matching.isEmpty())
		{
			clearRestock();
			return;
		}

		Map<Integer, Long> carried = countItems(InventoryID.INV, id -> true);
		Map<Integer, Long> worn = countItems(InventoryID.WORN, id -> true);
		Map<Integer, Long> held = new LinkedHashMap<>(carried);
		worn.forEach((id, quantity) -> held.merge(id, quantity, Long::sum));

		// Track the setup the player's current items look most like; on a tie, the newest one.
		RestockTargets targets = matching.get(matching.size() - 1);
		double best = -1;
		for (RestockTargets setup : matching)
		{
			double score = setup.similarity(held);
			if (score >= best)
			{
				best = score;
				targets = setup;
			}
		}

		RestockTargets chosen = targets;
		List<RestockTargets.Need> needs = chosen.calculate(carried, worn,
			countItems(InventoryID.BANK, chosen::includes));
		String title = setups.size() > 1
			? "Restock: setup " + (setups.indexOf(chosen) + 1) + " of " + setups.size()
			: "Restock";
		bankHighlightOverlay.update(needs);
		restockOverlay.update(needs, title);
	}

	private void clearRestock()
	{
		restockOverlay.clear();
		bankHighlightOverlay.clear();
	}

	private Map<Integer, Long> countItems(int containerId, IntPredicate wanted)
	{
		Map<Integer, Long> counts = new LinkedHashMap<>();
		ItemContainer container = client.getItemContainer(containerId);
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item != null && item.getId() >= 0 && item.getQuantity() > 0
					&& itemManager.getItemComposition(item.getId()).getPlaceholderTemplateId() == -1)
				{
					int id = itemManager.canonicalize(item.getId());
					if (wanted.test(id))
					{
						counts.merge(id, (long) item.getQuantity(), Long::sum);
					}
				}
			}
		}
		return counts;
	}

	private void resetSession()
	{
		++undoGeneration;
		lastSave = null;
		restockDirty = true;
		clearRestock();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			resetSession();
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		resetSession();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		resetSession();
	}

	private void message(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Inventory to Tab: " + message, null);
	}

	private static final class LastSave
	{
		private final String tag;
		private final Runnable undo;

		private LastSave(String tag, Runnable undo)
		{
			this.tag = tag;
			this.undo = undo;
		}
	}
}
