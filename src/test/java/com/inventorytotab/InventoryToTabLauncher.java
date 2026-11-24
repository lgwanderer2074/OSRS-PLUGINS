package com.inventorytotab;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class InventoryToTabLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(InventoryToTabPlugin.class);
		RuneLite.main(args);
	}
}
