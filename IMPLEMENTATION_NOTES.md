AKS Waybill – Report/User/Numbering Foundation

This version introduces the foundation agreed for the final letterhead report implementation:

1. Report / Company Profile is stored separately from client Companies.
   - Company Name
   - CR Number
   - VAT Number
   - Address
   - Phone
   - Email
   - The supplied report logo is included as an application resource.

2. User Management is implemented for administrators.
   - Username
   - Display Name
   - Waybill Code (user-specific Part 2)
   - Role (ADMIN / USER)
   - Enable/Disable
   - Password reset

3. Waybill numbering is now:
   Part 1 / Logged-in User Code / dd-MM-yyyy / Sequence
   Example: AKS/MAT/20-09-2026/1001

4. Existing databases are migrated automatically with a user_code column.
   Existing users receive a temporary derived code from their username if none exists; the administrator can edit it in User Management.

5. The supplied Word template and report logo are included:
   src/main/resources/com/aks/waybill/templates/transportation_waybill_template.docx
   src/main/resources/com/aks/waybill/images/report-logo.png

The final template-driven PDF/Word rendering is intentionally the next step. The current report generator remains in place so the application functionality is not removed while the final fixed-layout generator is rebuilt against the supplied template.
