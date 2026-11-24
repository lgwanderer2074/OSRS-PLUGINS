package com.inventorytotab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class RestockOverlay extends OverlayPanel
{
	private static final int VISIBLE_ROWS = 6;
	private final ItemManager itemManager;
	private volatile List<LayoutableRenderableEntity> rows = List.of();

	@Inject
	RestockOverlay(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_LEFT);
		// Draw over the bank interface, which otherwise covers the panel in smaller windows.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setPreferredSize(new Dimension(275, 0));
	}

	void update(List<RestockTargets.Need> needs)
	{
		List<LayoutableRenderableEntity> next = new ArrayList<>();
		next.add(TitleComponent.builder().text("Restock").build());
		if (needs.isEmpty())
		{
			next.add(LineComponent.builder().left("All saved quantities covered").build());
		}
		else
		{
			// Put items the player can withdraw first, so those rows clear as they bank.
			needs.sort(Comparator.comparing(need -> need.withdraw == 0));
			for (int i = 0; i < Math.min(VISIBLE_ROWS, needs.size()); i++)
			{
				RestockTargets.Need need = needs.get(i);
				String take = need.withdraw > 0 ? "Take " + need.withdraw : "";
				String shortage = need.shortfall > 0 ? "Short " + need.shortfall : "";
				String status = take.isEmpty() || shortage.isEmpty() ? take + shortage : take + ", " + shortage;
				next.add(LineComponent.builder()
					.left(itemManager.getItemComposition(need.itemId).getMembersName())
					.right(status)
					.rightColor(need.shortfall > 0 ? Color.ORANGE : Color.WHITE)
					.build());
			}
			if (needs.size() > VISIBLE_ROWS)
			{
				next.add(LineComponent.builder().left("+" + (needs.size() - VISIBLE_ROWS) + " more items").build());
			}
		}
		rows = next;
	}

	void clear()
	{
		rows = List.of();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<LayoutableRenderableEntity> current = rows;
		if (current.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().addAll(current);
		return super.render(graphics);
	}
}
