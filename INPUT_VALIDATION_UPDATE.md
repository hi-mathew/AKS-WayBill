# Input Validation Update

## Text field limits
The previous text-field limits have been doubled to provide more room for real-world data entry.

- Company Name: 300
- Contact Person: 200
- Address: 1000
- Phone: 100
- Email: 300
- Carrier: 300
- Driver: 200
- Vehicle / Trailer No.: 200
- Origin / Loading Point: 400
- Destination / Unloading Point: 400
- Package Type: 200
- Item Description: 600
- Username: 100
- Display Name: 200
- User Code: 20
- CR Number: 100
- VAT Number: 100
- Waybill Part 1: 60
- Waybill Sequence: 24

Search fields were also increased from 200 to 400 characters.

## Text areas
Special Instructions / Handling and Remarks have no character-length restriction. They support multi-line/paragraph content.

## Numeric fields
Quantity, Weight and Volume retain the existing numeric validation and limits:
- Quantity: up to 12 characters, up to 3 decimal places
- Weight: up to 15 characters, up to 3 decimal places
- Volume: up to 15 characters, up to 3 decimal places
- Negative values are rejected

Phone numbers, CR numbers and VAT numbers remain text fields and are not forced to numeric input.

Password fields remain capped at 128 characters and are intentionally not doubled because this is a security credential limit rather than a business-data field limit.
