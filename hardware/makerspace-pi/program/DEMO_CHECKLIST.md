# Hardware demonstration checklist

1. Install the matching app/launcher/Pi packages and pair the main entrance Pi once.
2. Power off the Pi, disconnect its monitor/keyboard/mouse, and power it on again.
   Do not open the scanner GUI. Tap a registered card; verify backend result and LED.
3. Create a member in admin → Assign card → tap, then remove and re-tap for access.
4. Test approved, denied, unknown and disabled cards. Verify audit rows and reasons.
5. Give selected-door and all-door overrides; test another Pi, expiration and revoke.
6. Unplug/reconnect the USB reader. Verify recovery without reopening an app and check
   Hardware events for the fault/recovery. Page one refreshes automatically.
7. Disconnect Wi-Fi, tap, restore Wi-Fi. Verify no offline approval; queued events
   upload once and do not cause any delayed unlock. Remove and tap again for access.
8. Power off the Pi while the backend stays running. After 45–55 seconds, check for
   one BACKEND CONNECTION_LOST. Power on and check CONNECTION_RESTORED and automatic
   scanning. Very short outages may not cross the timeout.
9. Use the door simulation for software timing only. Label it a simulation unless
   actual checked hardware is connected. Do not claim a real door physically opened.
10. To prove independence from the admin computer: use a deployed HTTPS backend,
    turn that computer off, and scan. A locally hosted backend goes down with it.
11. Review Pi/area/member/date/result filters and export the filtered CSV.
12. Delete a test member with a card. Check that the UID gets a normal denial, retains
    scan history and can be assigned to another member.
13. Confirm a member without admin permissions cannot view/manage hardware records.

The team's hosted URL/deployment and actual lock wiring are prerequisites for those
parts of the demo; they are not created by the headless service installer.
