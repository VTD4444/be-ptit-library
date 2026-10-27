# Module 2 Policies and configuration

The seven endpoints below implement section 2 of API.docx. They intentionally use
`/api/admin/...`; existing `/api/v1/...` APIs are unchanged. Authentication uses
the existing Bearer access token. No new dependencies or database changes are required.

## Endpoints

| Method and path | Role | Request | Successful data |
| --- | --- | --- | --- |
| GET `/api/admin/policies` | LIBRARIAN, ADMIN | None | Array of policy objects |
| PUT `/api/admin/policies/{id}` | ADMIN | Positive path ID and full policy body below | Updated policy object |
| GET `/api/admin/penalty-types` | LIBRARIAN, ADMIN | None | Array of penalty type objects |
| POST `/api/admin/penalty-types` | ADMIN | Penalty body without ID | Created penalty type object |
| PUT `/api/admin/penalty-types` | ADMIN | Full penalty body including positive `id` | Updated penalty type object |
| GET `/api/admin/search-configs` | ADMIN | None | Array of search configuration objects |
| PUT `/api/admin/search-configs/{id}/active` | ADMIN | Positive path ID; no body | Activated search configuration object |

All successful responses use HTTP 200 and the existing envelope:

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {},
  "timestamp": "2026-10-23T10:00:00Z"
}
```

The sample IDs and values below are illustrative. There is no seed script and no
guarantee these IDs or document types exist in a particular database.

## Policy examples

GET `/api/admin/policies` has no request body. Example `data`:

```json
[
  {"id":1,"userType":"STUDENT","docType":"BOOK","loanDays":14,"maxLoans":5,"maxRenewals":2,"holdExpiryDays":3}
]
```

PUT `/api/admin/policies/1` request:

```json
{"userType":"STUDENT","docType":"BOOK","loanDays":14,"maxLoans":5,"maxRenewals":2,"holdExpiryDays":3}
```

Its response `data` is the updated object, with `id: 1`, rather than an array.
`userType` must be one of the existing enum values: `STUDENT`, `LECTURER`,
`RESEARCHER`, `STAFF`. `docType` is a nonblank string matching the catalog's
document types; no new document-type enumeration is introduced.
`loanDays` and `maxLoans` must be positive; `maxRenewals` and `holdExpiryDays`
must be nonnegative. All six fields are required. `docType` is trimmed.

## Penalty type examples

GET `/api/admin/penalty-types` has no request body. Example `data`:

```json
[{"id":1,"code":"LATE","name":"Late return","unitName":"day","unitPrice":5000}]
```

POST `/api/admin/penalty-types` request:

```json
{"code":"LATE","name":"Late return","unitName":"day","unitPrice":5000}
```

Its response `data` is the created object including the generated ID.

PUT `/api/admin/penalty-types` request:

```json
{"id":1,"code":"LATE","name":"Late return","unitName":"day","unitPrice":6000}
```

Its response `data` contains the same ID and the updated price of 6000.
The update ID is in the JSON body to preserve the documented endpoint.
All business fields are required for PUT. Strings are trimmed without changing
case. Code and unit name are limited to 50 characters; name to 255 characters,
matching entity column lengths. Price is a nonnegative `Long`, preserving the
existing money representation; no currency or rounding rule is introduced.

Both writes check for an existing exact code; updating the same record with its
own code is allowed. This service check does not replace a database UNIQUE
constraint and cannot prevent duplicates from concurrent or external writers.
No constraint or schema migration is applied by this change.

## Search configuration examples

GET `/api/admin/search-configs` has no request body. Example `data`:

```json
[
  {"id":1,"chunkSize":512,"overlap":64,"strategy":"fixed","embeddingModel":"example-model","vectorDimensions":768,"isActive":false},
  {"id":2,"chunkSize":512,"overlap":64,"strategy":"fixed","embeddingModel":"example-model","vectorDimensions":768,"isActive":true}
]
```

PUT `/api/admin/search-configs/1/active` has no request body. Example response `data`:

```json
{"id":1,"chunkSize":512,"overlap":64,"strategy":"fixed","embeddingModel":"example-model","vectorDimensions":768,"isActive":true}
```

The service acquires `LOCK TABLE public.search_vector_configs IN EXCLUSIVE MODE`
inside the same JPA transaction, before loading any configurations. It sets the
selected row active and all other rows inactive, then flushes before returning.
Repeating the request is safe and also repairs pre-existing multiple-active flags.
Missing IDs fail before any flags are changed. A transaction failure rolls back
all changes and releases the lock.

This PostgreSQL table lock serializes activations across Backend instances and
blocks INSERT/UPDATE/DELETE from external writers while the transaction holds it.
Ordinary SELECT remains allowed. External writers may violate the invariant
after the lock is released; an enduring database invariant would require an
approved constraint or a shared write protocol. The role executing activation
must have the PostgreSQL privileges required to acquire this lock and update rows.
Long-running external transactions can delay activation.

Activation does not change the embedding model, vector dimensions, existing
chunks, or embeddings and does not trigger reindexing. A newly active config may
have no indexed data or incompatible embeddings; compatibility must be established
by the indexing pipeline before activation.

## Errors

| HTTP | Body code | Meaning |
| --- | --- | --- |
| 400 | 1001 | Missing/invalid input, enum, JSON, path ID, or validation failure |
| 401 | 1100 | Missing or invalid authentication |
| 403 | 1101 | Authenticated user lacks the required role |
| 404 | 1002 | Policy or penalty type ID not found |
| 404 | 1401 | Search configuration ID not found |
| 409 | 1003 | Penalty code already used by another record |

Successful DTOs contain only the business fields shown above; requests cannot
update entity IDs except selecting the penalty record, timestamps, or other
internal fields. Invalid-input handling for malformed JSON and path validation
is scoped to the new controllers. Existing module error handling is unchanged.

## Verification

From the repository root, with JAVA_HOME pointing to JDK 17:

```powershell
.\mvnw.cmd -o test
```

Offline mode requires dependencies to have been cached. Otherwise run
`.\mvnw.cmd test` to allow normal dependency resolution. The default tests use
mocked repositories/services and MVC slices, not the application's datasource.
They cover DTOs, validation, duplicate/not-found errors, all seven endpoints'
role matrix, anonymous rejection, and representative existing API routes.

`SearchConfigurationPostgresTest` is opt-in and does not load application.yml
or use DB_URL. It verifies real activation, repeat activation, rollback after
flush, and concurrent transactions. It requires an explicitly provided EMPTY,
disposable localhost PostgreSQL database with a name starting `module2_test`.
It refuses an existing `public.search_vector_configs` table, creates its own
minimal table and two fixture rows, and drops only its owned table afterwards.

After a disposable database has been prepared, use a separate PowerShell session:

```powershell
$env:MODULE2_POSTGRES_TESTS = 'true'
$env:MODULE2_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/module2_test'
$env:MODULE2_TEST_DB_USERNAME = '<TEST_DATABASE_USERNAME>'
$testPassword = Read-Host 'Disposable test database password' -AsSecureString
$env:MODULE2_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $testPassword).Password
Remove-Variable testPassword
.\mvnw.cmd -o '-Dtest=SearchConfigurationPostgresTest' test
```

Never point these tests at Supabase or a shared database. The three PostgreSQL
tests are skipped unless explicitly enabled. Their presence alone is not proof
that PostgreSQL rollback/concurrency has been verified on the current machine.
