# 25 · A corrupted credentials file can log a slice of the refresh token

**Severity:** low · **Area:** `:data:source:remote:implementation` (`SyncCredentialsStore.read`)

`SyncCredentialsStore.kt:51` prints `exception.message`; kotlinx.serialization's `JsonDecodingException` appends
`JSON input: …` with a window of the input around the failing offset, which for this document can be a piece of a
token.

## Fix

Log the exception's class and nothing of its message: `println("Could not read the sync credentials: ${exception::class.simpleName}")`.
Do the same in `SyncStateLocalSourceImpl.read` for the credentials file (the index can keep its message). Add a
one-line comment saying why.
