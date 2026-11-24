package com.inventorytotab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines bank items the saved setup still needs and labels them with the amount to withdraw.
 * It only draws; the player withdraws with the normal bank controls.
 */
final class BankHighlightOverlay extends WidgetItemOverlay
{
	private static final Color OUTLINE = new Color(0, 255, 120);
	private static final Color FILL = new Color(0, 255, 120, 40);

	private volatile Map<Integer, Long> withdrawals = Map.of();
	// Items already labelled this frame, so a repeated layout slot does not show the number twice.
	private final Set<Integer> labelled = new HashSet<>();

	@Inject
	BankHighlightOverlay()
	{
		showOnBank();
	}

	void update(List<RestockTargets.Need> needs)
	{
		withdrawals = needs.stream()
			.filter(need -> need.withdraw > 0)
			.collect(Collectors.toUnmodifiableMap(need -> need.itemId, need -> need.withdraw));
	}

	void clear()
	{
		withdrawals = Map.of();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (withdrawals.isEmpty())
		{
			return null;
		}
		labelled.clear();
		return super.render(graphics);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// Placeholders use their own item IDs, so they never match a withdrawal.
		Long amount = withdrawals.get(itemId);
		Widget widget = widgetItem.getWidget();
		if (amount == null || widget == null || widget.getParentId() != InterfaceID.Bankmain.ITEMS)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(FILL);
		graphics.fill(bounds);
		graphics.setColor(OUTLINE);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

		if (!labelled.add(itemId))
		{
			return;
		}
		String text = String.valueOf(amount);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + bounds.width - metrics.stringWidth(text) - 1;
		int y = bounds.y + bounds.height - 1;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(OUTLINE);
		graphics.drawString(text, x, y);
	}
}
