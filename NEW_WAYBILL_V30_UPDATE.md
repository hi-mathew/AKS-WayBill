# W.A.S.P 1.4.0 - v30 New Waybill / Company Snapshot Update

## Company persistence model

Shipper / Consignor and Consignee / Receiver values are now stored as a snapshot on each waybill, matching the existing Carrier / Transit master-data pattern.

The waybill stores:
- Company name
- Contact person
- Address
- Phone number
- Email address

The legacy `shipper_company_id` and `consignee_company_id` columns are retained for database compatibility, but new saves leave them NULL. Existing waybills are migrated by copying their current company values into the snapshot columns and then clearing the legacy FK values.

This means:
- Deleting a company master does not delete or modify historical waybills.
- Company delete is independent of Active / Inactive status.
- Activate / Deactivate remains available as a separate administrative function.
- Saved Waybill searches and reports use the stored snapshot values.

## Selection controls

The reusable company, carrier and location selectors now retain a visible first option:
- `— Select saved company —`
- `— Select saved carrier —`
- `— Select or type below —`

Selecting that option again clears the corresponding populated fields.

## New Waybill UI refinements

- Reduced excessive vertical spacing in Shipper / Consignee panels.
- Reduced spacing in Carrier & Transit Details.
- Simplified the combined Parties section heading.
- Kept the existing two-column structure.
- Kept the established W.A.S.P color scheme and controls.
- Party detail fields behave like snapshot fields and remain editable after selecting a master record.
- Existing waybills load their stored party snapshot even when the corresponding master has subsequently been deleted.
