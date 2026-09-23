# W.A.S.P 1.4.0 Implementation Notes

## Branding
- Product name: W.A.S.P
- Full form: Waybill Automation & Shipping Platform
- AKS Global Logistics remains the company identity and application/report logo.
- `WASPLogo.png` is used as the application/window icon and converted to `packaging/wasp.ico` for the Windows installer/shortcut.

## Responsive application shell
- Initial window sizing is calculated from the primary screen's visual bounds.
- The main workspace remains resizable and waybill/settings screens use scrollable content.
- Dashboard has visible Minimize and Close controls.
- Login has a visible Close button.
- Close and logout operations use centralized confirmation, unsaved-data protection and backup reminder flows.

## Waybill lifecycle
- New waybills start as `DRAFT`.
- Draft reports receive a `DRAFT` watermark.
- Final reports have no watermark.
- Normal users can edit their own Draft waybills.
- Final waybills are locked for normal users.
- Administrators can edit Final waybills.
- Administrators can return Final waybills to Draft, with a mandatory reason recorded in the audit log.
- Administrators can delete waybills after confirmation.

## Terms & Conditions
- T&C clauses are stored in SQLite and seeded on first initialization.
- Administrators can add, edit, delete and reorder clauses.
- Reports populate T&C from active configured clauses.
- The forced page break before T&C is removed; T&C follows the Waybill/Remarks content naturally.

## Master data deletion
- Company masters are protected from destructive deletion when historical waybills reference them. They are deactivated instead, preserving historical reports.
- Unused company masters can be deleted by an Administrator after confirmation.
- Saved carrier/location master records can be deleted by an Administrator after confirmation. Existing waybills store their operational values independently, so deleting a saved master does not change historical waybills.

## Critical actions
- Clear/Reset/Delete/Logout/Exit operations require confirmation.
- Unsaved waybill changes are detected and offer Save & Exit/Logout, Exit/Logout Without Saving, or Cancel.
- A backup reminder is displayed before logout/exit with Backup Now, Continue Without Backup and Cancel.

## Backup
- Existing manual backup/restore remains available to Administrators.
- Exit/logout reminder uses the application's default timestamped backup destination for Backup Now.
- Configurable automatic backup scheduling remains an optional follow-up enhancement; the mandatory backup reminder is included in this release.

## About / footer
- About content uses the supplied W.A.S.P description, developer and owner information.
- The dashboard/sidebar includes the requested small-font product/copyright/developer footer.

## Packaging
- Version bumped to 1.4.0.
- Windows jpackage name is `W.A.S.P`.
- Installer uses `packaging/wasp.ico`.
- The custom runtime retains `jdk.xml.dom`, required by the working PDF generation stack.
