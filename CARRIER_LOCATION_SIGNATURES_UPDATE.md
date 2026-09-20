# Carrier, Location, Company Dropdown & Signature Update

Implemented requested changes:

- Saved Carrier master now stores Carrier Name, Driver Name and Vehicle / Trailer No.
- Selecting a saved carrier populates all three carrier/transit fields.
- Manually entered carrier details are automatically added/updated in Saved Data when the waybill is saved.
- Saved Location remains reusable for Origin / Loading Point and Destination / Unloading Point.
- Manually entered locations are automatically added to Saved Data when the waybill is saved.
- Saved Data > Carriers now shows and edits all three carrier details.
- Company selector now has an explicit saved-company dropdown in addition to manual entry and autocomplete.
- Company autocomplete remains non-destructive: typing does not automatically select a suggestion.
- Existing company selection continues to populate company details and make them read-only.
- Report declaration/signature output now places the entered name directly after Signature / Driver Signature / Receiver Signature, without a separate Name/Driver Name/Receiver Name line.
- Existing declaration dates remain on the next line.
- Existing date validation, input limits and numeric validation are retained.
- Existing Shippers and Consignees Saved Data tabs are not introduced; Companies remains the single company master.
- Database migration automatically adds the new saved-carrier driver and vehicle columns to an existing SQLite database.
