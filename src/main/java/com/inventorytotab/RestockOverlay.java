package com.inventorytotab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The Restock panel. Alt-drag its edges to resize it: width follows the drag, and the
 * height decides how many item rows fit before the rest collapse into "+N more items".
 */
final class RestockOverlay extends OverlayPanel
{
	private static final int DEFAULT_ROWS = 6;
	private static final int DEFAULT_WIDTH = 275;
	private static final int MINIMUM_WIDTH = 140;
	// Panel border above and below the content, plus the gap after each line.
	private static final int VERTICAL_PADDING = 8;
	private static final int LINE_GAP = 1;

	private final ItemManager itemManager;
	private volatile LayoutableRenderableEntity title;
	private volatile List<LayoutableRenderableEntity> rows = List.of();

	@Inject
	RestockOverlay(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_LEFT);
		// Draw over the bank interface, which otherwise covers the panel in smaller windows.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setResizable(true);
		setMinimumSize(MINIMUM_WIDTH);
		panelComponent.setPreferredSize(new Dimension(DEFAULT_WIDTH, 0));
	}

	void update(List<RestockTargets.Need> needs, String heading)
	{
		List<LayoutableRenderableEntity> next = new ArrayList<>();
		if (needs.isEmpty())
		{
			next.add(LineComponent.builder().left("All saved quantities covered").build());
		}
		else
		{
			// Put items the player can withdraw first, so those rows clear as they bank.
			List<RestockTargets.Need> sorted = new ArrayList<>(needs);
			sorted.sort(Comparator.comparing(need -> need.withdraw == 0));
			for (RestockTargets.Need need : sorted)
			{
				String take = need.withdraw > 0 ? "Take " + need.withdraw : "";
				String shortage = need.shortfall > 0 ? "Short " + need.shortfall : "";
				String status = take.isEmpty() || shortage.isEmpty() ? take + shortage : take + ", " + shortage;
				next.add(LineComponent.builder()
					.left(itemManager.getItemComposition(need.itemId).getMembersName())
					.right(status)
					.rightColor(need.shortfall > 0 ? Color.ORANGE : Color.WHITE)
					.build());
			}
		}
		title = TitleComponent.builder().text(heading).build();
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

		int visible = visibleRows(graphics);
		panelComponent.getChildren().add(title);
		if (current.size() <= visible)
		{
			panelComponent.getChildren().addAll(current);
		}
		else
		{
			// Keep the last visible line for the count of hidden items.
			int shown = Math.max(1, visible - 1);
			panelComponent.getChildren().addAll(current.subList(0, shown));
			panelComponent.getChildren().add(LineComponent.builder()
				.left("+" + (current.size() - shown) + " more items")
				.build());
		}
		return super.render(graphics);
	}

	private int visibleRows(Graphics2D graphics)
	{
		Dimension size = getPreferredSize();
		if (size == null || size.height <= 0)
		{
			return DEFAULT_ROWS;
		}
		int lineHeight = graphics.getFontMetrics(FontManager.getRunescapeSmallFont()).getHeight() + LINE_GAP;
		int titleHeight = graphics.getFontMetrics(FontManager.getRunescapeBoldFont()).getHeight() + LINE_GAP;
		return Math.max(1, (size.height - VERTICAL_PADDING - titleHeight) / lineHeight);
	}
}
