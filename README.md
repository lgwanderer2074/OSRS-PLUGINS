# Inventory to Tab

A RuneLite plugin that saves your inventory arrangement and optionally your equipped gear into a Bank Tag tab you choose, then shows what to withdraw to restock that setup.

## Use it

1. Enable **Bank Tags** and its **Use Tag Tabs** setting.
2. Arrange your inventory as you want to save it, including repeated food or potions.
3. Open the bank and click the desired tag tab down the left. Create a tag tab first if you need a new one.
4. Right-click that tab's icon and select **Save gear and inventory here** or **Save inventory here**.
5. If the tab already has a layout, choose **Add below (keep existing setups)** to stack the new setup underneath with one empty row between them, **Replace all** to put only this setup at the top, or **Cancel**. A first save into a tab without a layout applies immediately.

A tab can hold several setups this way. The restock list follows whichever saved setup your current inventory and gear most resemble, and its title shows which one (for example **Restock: setup 2 of 3**). With nothing in your inventory or gear, it follows the newest setup.

The combined layout places equipped items in their familiar equipment panel positions on the left and the four-column inventory on the right. The inventory-only action uses the left four columns. Empty slots and repeated items keep their positions. Other items in the tab follow below the layout. Containers such as the rune pouch are saved as individual items.

The layout belongs to core Bank Tags and persists across client restarts using its usual RuneLite profile storage. It remains usable with Bank Tags after disabling Inventory to Tab.

### What the copy contains

Open the saved tab to see **Restock**. **Take** shows how many items you still need and can withdraw. **Short** shows how many more you need beyond your bank stock. The list updates when your bank, inventory, or equipment changes. It lists items you can withdraw first. Hold Alt and drag the panel to move it, or Alt-drag its edges to resize it; making it taller shows more items before the rest collapse into **+N more items**. Bank items you can withdraw are outlined in green, with the number to take in the corner. Withdraw them with the normal bank controls; the plugin never withdraws for you. Right-click any tag tab and choose **Hide restock list** or **Show restock list** to turn the panel and highlights off or on. When you turn it on, a chat message explains if the open tab has nothing saved or its saved items were moved.

The checklist counts matching items in your inventory and counts equipped items toward saved gear quantities. Potion doses count separately. Bank stock comes from the main bank container. Potion storage and supplies inside pouches or other containers require a separate check. Notes use the normal bank item representation.

Use **Undo last setup save** from the same tab’s right-click menu to restore its previous layout, tags, and saved quantities. You can close and reopen the bank during the same session. A later save, account or profile change, world hop, or layout edit can invalidate Undo.

Quantities are stored for your character and RuneLite profile. Resave an older layout to start tracking its quantities. If you rename a tab or move its saved items, save it again to attach the checklist to the new layout.

**Later additions:** Core Bank Tags can put newly tagged items or newly banked potion variants into saved empty spaces on a later opening. This version copies the current arrangement; it does not lock the layout against future changes. Save the inventory again to rebuild its block.

## Development

Version **0.5.0**. To run it from source:

1. Install a JDK compatible with the Gradle wrapper; **JDK 17** was used for this build. The compiled plugin targets Java 11.
2. Open this folder as a Gradle project in IntelliJ IDEA, or use a terminal.
3. Run the Gradle **run** task. From a terminal in this folder:

   ```sh
   ./gradlew run
   ```

4. In the development RuneLite client, search for **Inventory to Tab** in the plugin list and enable it. Enable core **Bank Tags** too.

For a Jagex Account, follow RuneLite's [development-client login guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). The official [Plugin Hub development guide](https://github.com/runelite/plugin-hub#creating-new-plugins) explains the general setup.

### Build and test

```sh
./gradlew test build
```

The source uses RuneLite's current `latest.release` dependency, as the official template recommends. Development APIs can change, so rebuilding against a future release may require an update. The JAR is written under `build/libs/`.

### Verification

The unit tests cover both layout modes, restock quantities and shortfalls, exact potion doses, equipped stack counts, and changes to the saved layout. Saving, overwrite confirmation, the restock panel, Show/Hide restock list, and bank highlights have been checked in a logged-in game session. [MANUAL_TESTS.md](MANUAL_TESTS.md) lists the full in-game checklist.

### Code map

- `src/main/java/com/inventorytotab/InventoryToTabPlugin.java` — tab context menu, inventory capture, Bank Tags integration, restock refresh and Undo.
- `src/main/java/com/inventorytotab/InventoryLayoutBuilder.java` — slot mapping and preservation of other items.
- `src/main/java/com/inventorytotab/RestockTargets.java` — saved quantities and what is still needed.
- `src/main/java/com/inventorytotab/RestockOverlay.java` — the Restock panel.
- `src/main/java/com/inventorytotab/BankHighlightOverlay.java` — green bank outlines with amounts to withdraw.
- `src/test/java/com/inventorytotab/InventoryLayoutBuilderTest.java` — layout regression tests.
- `src/test/java/com/inventorytotab/InventoryToTabLauncher.java` — development-client launcher.

Both save actions also tag the captured items into the selected tab through the public [Bank Tags APIs](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/plugins/banktags/package-summary.html).
