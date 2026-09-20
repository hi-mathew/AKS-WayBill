# Report page-flow fix

This version corrects the page-flow behavior using the supplied transportation_waybill.docx as the source of truth.

Changes:
- Removed the programmatic page break before Terms & Conditions. The supplied template already contains the intended page break.
- Removed the page-break run embedded in the template's Remarks paragraph so Remarks remains on Page 1.
- Signature row is marked as non-splittable so signature labels and signature lines cannot separate across pages.
- Dynamic item values are written at the template's 8.5pt size instead of the default Word font size.
- Long special-instruction text is reduced modestly in font size to keep the Page 1 declaration/signature area within the approved layout.
- Remarks is written at the template's 8.5pt size with compact paragraph spacing.

IMPORTANT: After replacing the project, use IntelliJ Maven -> Lifecycle -> clean, then Maven -> Plugins -> javafx -> javafx:run. This ensures old compiled report templates/resources are removed from target/classes.
