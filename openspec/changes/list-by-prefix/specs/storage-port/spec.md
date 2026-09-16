## ADDED Requirements

### Requirement: List objects by key prefix
`ObjectStorage` SHALL gain a `list(prefix)` method once a real caller needs to enumerate stored objects the database
does not already track, such as a reconciliation job finding objects no row points at.

#### Scenario: A reconciliation job enumerates objects under a prefix
- **WHEN** a reconciliation job needs to find objects that exist in the store but are not referenced by any database
  row
- **THEN** `ObjectStorage` offers a `list(prefix)` method to enumerate the keys under that prefix
