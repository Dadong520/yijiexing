# Public experience authentication

The `driver` Cloud Run role serves both driver and passenger clients. It no longer
requires or validates `X-Demo-Access`; omitted and obsolete header values have the
same behavior. `access_code`/`DEMO_ACCESS_CODE` remain accepted as legacy server
configuration, but are not client credentials and are not placed in Android code.

The existing role-specific login, bearer sessions, phone allowlists and business
request IDs still apply. `/api/state` and `/api/action` require a driver token;
`/api/passenger/*` requires a passenger token except its code/login endpoints.
Both login code endpoints are explicitly simulated; no real SMS is sent.

The public role rejects administration and shutdown routes even when a caller
supplies an internal header. `CloudService.action` still denies `local.reset`
with `403 / DEMO_OPERATION_DISABLED`. Unknown administration action names cannot
be executed through the driver action dispatcher.

The separate `admin` role retains its required internal admin token and application
session/RBAC. Cloud Run IAM, browser IAP, Google account access and cloud secret
configuration are deployment concerns unchanged by this patch. The copied static
files here are the v8 reference; publishing an admin image must preserve the current
browser adapter and current management Web assets rather than use these reference
assets as an upgrade.

`tests/test_public_experience.py` exercises the public and admin roles over actual
loopback HTTP with temporary SQLite and the existing in-memory GCS generation/CAS
emulator. No external cloud, original environment, real payment or live account is
used. No schema, order/amount logic, persistence or billing behavior is changed.
