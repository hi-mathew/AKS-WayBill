# AKS Waybill - Companies Master Integration

This build integrates the single Company Master into the working New/Edit/View Waybill flow.

## Company behavior

- Shipper and Consignee use the same `company` master table.
- The company selector uses `ComboBox<String>` rather than an editable `ComboBox<CompanyRecord>` to avoid the JavaFX editable-combo `String`/domain-object cast problem.
- Existing companies can be searched by company name, contact, phone, or email.
- Selecting an existing company automatically populates contact, address, phone, and email and makes those fields read-only on the waybill.
- A new company name can be typed directly into a waybill. Its contact/address/phone/email fields remain editable.
- When the waybill is saved, a new company is inserted into the master automatically if the name does not already exist.
- The `+ New Company` button remains available for creating a company explicitly from the waybill screen.
- The Companies screen supports search, add, edit, activate/deactivate, pagination, and double-click edit.

## Existing waybill behavior

- View remains read-only.
- Edit keeps the selected company linked to the master.
- The waybill number remains unchanged during edit.
- New waybill numbering continues to be allocated only during save.

## Database

The SQLite database remains in the existing project/application `data` directory. No external database installation is required.

## Running in IntelliJ IDEA

Use the Maven tool window and run:

`Plugins -> javafx -> javafx:run`

The project uses JDK 21 and JavaFX 21.0.6.

## Report Generation
- Saved Waybills provides a Docs menu with Generate PDF and Generate Word.
- View Waybill provides Generate PDF and Generate Word.
- PDF uses OpenPDF and Word uses Apache POI.
- Generated filenames are based on the waybill number and the user chooses the save location.
