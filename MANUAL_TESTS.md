# In-game verification

Status: pending a logged-in development RuneLite session.

1. **Fresh tab.** Create and open a Bank Tag tab. Wear gear with a few empty equipment slots and carry a mixed inventory with gaps and repeated food. Choose **Save gear and inventory here**. Confirm gear appears on the left and the right four columns match all seven inventory rows. Repeat with **Save inventory here** to check the inventory-only action.
2. **Existing tab.** Open a tab with a custom layout and start a save. Dismiss the confirmation and verify the layout and quantities stay as they were. Repeat and choose **Save setup**. Verify other items appear below the seven inventory rows. Close and reopen the bank, open the saved tab, and choose **Undo last setup save** from its right-click menu. Check that the old layout returns.
3. **Tag membership.** Include an inventory item without the tag, save, and deposit that item normally. Confirm it appears in the saved slot. Reopen the bank and restart the client to check persistence.
4. **Undo new memberships.** Repeat with a different previously untagged item, then use **Undo last setup save**. Confirm the item loses only the tag added by the save.
5. **Disabled layout.** Use a tab without a layout. Save, then use **Undo last setup save**. Confirm the tab returns to its normal filtered arrangement.
6. **Potion variants:** Use a tab with variation tags and banked 1/2/3/4-dose potions. Save an inventory containing one dose variant with empty slots nearby. Confirm the other variants remain below the inventory block. Confirm Undo succeeds.
7. **Bank controls:** Withdraw from repeated item slots, deposit normally, use placeholders, and switch between normal tabs, tag tabs, and potion storage. Check item quantities and menu actions behave normally.
8. **Inactive target:** Right-click a tag tab that is not open and select Save inventory here. Confirm the message asks you to open it first and nothing is saved. Open it normally and retry.
9. **Empty inventory:** With gear equipped and an empty inventory, confirm the combined action saves the gear on the left. Confirm the inventory-only action leaves the tab as it was.
10. **Newer edits.** Save, alter the layout, and attempt **Undo last setup save**. Confirm it protects the newer layout.
11. **Account/profile changes.** Save, then log out, hop worlds, or switch RuneLite profiles. Check that the correct character and profile own the saved quantities and the previous Undo is gone.
12. **Noted items and containers:** Save noted items and a rune pouch. Confirm noted items use their bank representation and the pouch itself is saved without expanding its contents.
13. **Future changes.** After saving, add a newly banked fuzzy potion variant or a newly tagged item. Reopen the tag. Record where core Bank Tags places the new item. Saving again should rebuild the inventory block.
14. **Restock counts.** Save six four-dose prayer potions. Carry two and leave three in the main bank. Confirm **Take 3, Short 1**. Withdraw one and confirm **Take 2, Short 1**. Add three-dose potions and verify the target still counts four-dose potions separately. Close the bank or change tabs and check that the panel switches off or follows the saved tab. Reopen the saved tab after restarting the client to check that the target quantity persists.

Record the RuneLite version and any unexpected behavior when reporting a problem.
