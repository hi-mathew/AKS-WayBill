# W.A.S.P 1.4.0 - v21 Dashboard Changes

- Dashboard overview restored to two explicit three-card rows:
  1. Waybills this month / Total saved waybills / Saved companies
  2. New Waybill / Saved Waybills / Saved Data
- Card height remains compact; cards expand horizontally across the maximized workspace.
- Admin Settings and User Management use two equal-width cards.
- Recent Waybills table receives enough calculated height for five fixed-height rows to avoid an unnecessary internal scrollbar when all five records fit.
- Login window explicitly restores to normal size on every logout/login cycle.
- Dashboard maximization is scheduled on the next JavaFX pulse after every successful login, including subsequent logins after sign-out.
