# New Master Data Buttons Update

## Waybill Form

Added quick-create actions directly to the Carrier & Transit Details section:

- `+ New Carrier` beside Select Saved Carrier
- `+ New Location` beside Origin / Loading Point
- `+ New Location` beside Destination / Unloading Point

### New Carrier
The dialog captures:
- Carrier Name
- Driver Name
- Vehicle / Trailer No.

After saving, the carrier is added/refreshed in Saved Data and immediately selected in the waybill form with all three values populated.

### New Location
The dialog captures the reusable Location Name. The location is added/refreshed in Saved Data and immediately selected in the corresponding Origin or Destination field.

The existing `+ New Company` button remains unchanged for both Shipper and Consignee because both use the same Company master.

The new buttons are disabled in View mode.
