# Saved Data and Signatures/Declarations Update

This build adds the reusable master data and declaration fields requested from the existing web application screenshots.

## Saved Data
- Companies remains the shared master for Shipper/Consignor and Consignee/Receiver.
- Separate Shippers and Consignees masters are intentionally not included.
- Carriers can be added, edited, activated/deactivated, searched and selected from New/Edit Waybill.
- Locations can be added, edited, activated/deactivated, searched and selected for Origin/Loading Point and Destination/Unloading Point.
- The New Waybill screen keeps manual entry available for Carrier, Origin and Destination.

## Signatures & Declarations
Each waybill stores:
- Shipper Declaration: Name + Date
- Carrier Receipt: Driver Name + Date
- Consignee Proof of Delivery: Receiver Name + Date

The values are displayed in the New/Edit/View waybill form and populated into the approved Word/PDF report template.

## Database migration
Existing databases are upgraded automatically with the new saved-data tables and waybill declaration columns. No manual SQL migration is required.

## Existing validation retained
- Text field limits from v4 remain doubled from the original limits.
- Special Instructions and Remarks remain unrestricted text areas.
- Quantity, Weight and Volume retain numeric validation.
- Estimated Delivery Date and declaration dates support calendar selection and manual dd-MM-yyyy entry, with invalid dates rejected on save.
- Company autocomplete does not overwrite user-typed text unless a suggestion is explicitly selected.
