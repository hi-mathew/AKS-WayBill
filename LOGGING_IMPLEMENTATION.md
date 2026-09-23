# W.A.S.P 1.4.0 - Technical Logging

W.A.S.P now has centralized technical logging using the JDK `java.util.logging` API.

## Log location

Packaged Windows installation:

`%LOCALAPPDATA%\\AKS-Waybill\\logs\\wasp.log`

Development runs continue to use the existing project-local `data` directory, so the log is under:

`<project>\\data\\logs\\wasp.log`

## Rotation

The log rotates at approximately 5 MB and keeps five log files.

## Audit vs technical logging

- `AuditLogService` records business actions such as CREATE, UPDATE, FINALIZE, FINAL_TO_DRAFT, DELETE, BACKUP and RESTORE.
- `WaspLogger` records technical application events and full exception stack traces for troubleshooting.

Passwords and secrets must not be written to the technical log.

## Finalization diagnostics

Waybill finalization logs the request, successful completion, and any exception that causes a rollback. This makes database/permission problems diagnosable without relying only on the generic UI message.
