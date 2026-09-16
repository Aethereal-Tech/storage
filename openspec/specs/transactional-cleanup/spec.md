# Transactional cleanup

## Purpose

Transactional cleanup covers `AfterCommit`, the helper that deletes a stored object only once the transaction that
dropped its owning row has actually committed.

## Requirements

### Requirement: AfterCommit deletes after commit, does nothing on rollback, and deletes immediately with no active transaction
`AfterCommit.delete(storage, key)` SHALL register a `TransactionSynchronization` that deletes the object after the
enclosing transaction commits, SHALL do nothing on rollback, and SHALL delete immediately when no transaction is
active.

#### Scenario: Delete happens after a successful commit
- **WHEN** `AfterCommit.delete(storage, key)` is called inside a transaction that later commits
- **THEN** the object is deleted only after the commit completes

#### Scenario: No delete happens on rollback
- **WHEN** `AfterCommit.delete(storage, key)` is called inside a transaction that later rolls back
- **THEN** the object is never deleted

#### Scenario: Delete happens immediately with no active transaction
- **WHEN** `AfterCommit.delete(storage, key)` is called with no transaction active
- **THEN** the object is deleted immediately

### Requirement: Delete the row first, the object after the commit
The row referencing an object SHALL be deleted FIRST, and the object itself SHALL be deleted AFTER the commit. This
way a failed object delete leaks an unreferenced object; the other order would leave a live row pointing at bytes
that are gone.

#### Scenario: Row deletion precedes object deletion
- **WHEN** a photo (or any owning row) is deleted alongside its stored object
- **THEN** the database row is deleted first, within the transaction, and the object delete is registered to run
  after that transaction commits

### Requirement: A failure after commit is logged, not thrown; the immediate path throws
A failure in the after-commit delete path SHALL be logged at WARNING, not thrown — the caller's work already
succeeded, and propagating would fail a request that did everything right. The immediate delete path (no active
transaction) SHALL throw, because there is no committed work it would contradict.

#### Scenario: A failure after commit is logged rather than propagated
- **WHEN** the object delete registered to run after commit fails
- **THEN** the failure is logged at WARNING
- **AND** it is not thrown to the caller, whose request already succeeded

#### Scenario: A failure with no active transaction is thrown
- **WHEN** `AfterCommit.delete` runs immediately, with no active transaction, and the delete fails
- **THEN** the failure is thrown, since there is no already-committed work it would contradict
