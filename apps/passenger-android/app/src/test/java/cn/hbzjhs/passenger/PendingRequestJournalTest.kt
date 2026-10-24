package cn.hbzjhs.passenger

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PendingRequestJournalTest {
  private val payload = """{"content":"journal regression"}"""

  private class Disk(var content: String = "") {
    var failOnWrite: Int? = null
    var writes = 0

    fun save(value: String) {
      writes++
      if (writes == failOnWrite) throw IOException("simulated disk failure")
      content = value
    }
  }

  private class Ids {
    var generated = 0

    fun next() = "request-${++generated}"
  }

  private fun journal(disk: Disk, ids: Ids) =
    PendingRequestJournal(load = { disk.content }, save = disk::save, createId = ids::next)

  @Test
  fun uncertainCommitThen401And403SurviveStorageRebuildAndAccountRelogin() = runBlocking {
    val disk = Disk()
    val ids = Ids()
    val sent = mutableListOf<String>()
    // This is the same production execute() used for every Store action and attachment upload.
    // The first request may have committed remotely; later auth failures cannot negate that.
    listOf(503, 401, 403).forEachIndexed { attempt, status ->
      val restored = journal(disk, ids)
      val result = runCatching {
        restored.execute("passenger-A", "feedback.submit", payload, "session-$attempt") { id ->
          sent += id
          throw ApiError(status, "simulated transport response")
        }
      }
      assertTrue(result.exceptionOrNull() is ApiError)
      assertEquals(1, JSONObject(disk.content).length())
    }
    val reloggedIn = journal(disk, ids)
    val result =
      reloggedIn.execute("passenger-A", "feedback.submit", payload, "new-session-after-login") { id
        ->
        sent += id
        "confirmed previous commit"
      }
    assertEquals("confirmed previous commit", result)
    assertEquals(1, sent.distinct().size)
    assertEquals(1, ids.generated)
    assertEquals(0, JSONObject(disk.content).length())
    reloggedIn.execute("passenger-A", "feedback.submit", payload, "new-session-after-login") { id ->
      assertNotEquals(sent.first(), id)
    }
  }

  @Test
  fun stableAccountIdentitySeparatesDifferentPassengersAndDifferentActions() = runBlocking {
    val disk = Disk()
    val ids = Ids()
    val captured = mutableMapOf<String, String>()
    suspend fun fail(account: String, action: String, label: String, token: String) {
      val failure =
        runCatching {
            journal(disk, ids).execute(account, action, payload, token) { id ->
              captured[label] = id
              throw IOException("no response")
            }
          }
          .exceptionOrNull()
      assertTrue(failure is IOException)
    }
    fail("passenger-A", "feedback.submit", "a-first", "old-token")
    fail("passenger-B", "feedback.submit", "b", "other-token")
    fail("passenger-A", "favorite.save", "a-other-action", "old-token")
    fail("passenger-A", "feedback.submit", "a-relogin", "new-token")
    assertEquals(captured["a-first"], captured["a-relogin"])
    assertNotEquals(captured["a-first"], captured["b"])
    assertNotEquals(captured["a-first"], captured["a-other-action"])
    assertEquals(3, JSONObject(disk.content).length())
  }

  @Test
  fun storageFailuresBlockTransportAndPreserveAnAcknowledgedButUnclearedRequest() = runBlocking {
    val disk = Disk().apply { failOnWrite = 1 }
    val ids = Ids()
    val active = journal(disk, ids)
    var calls = 0
    val failedPreparation = runCatching {
      active.execute("passenger-A", "feedback.submit", payload) {
        calls++
        "accepted"
      }
    }
    assertTrue(failedPreparation.exceptionOrNull() is PendingStorageException)
    assertEquals(0, calls)
    assertEquals("", disk.content)

    disk.failOnWrite = 3 // write 2 prepares the key; write 3 acknowledges the successful response.
    var acceptedKey = ""
    val failedAcknowledgement = runCatching {
      active.execute("passenger-A", "feedback.submit", payload) { id ->
        calls++
        acceptedKey = id
        "accepted"
      }
    }
    assertTrue(failedAcknowledgement.exceptionOrNull() is PendingStorageException)
    assertEquals(1, calls)
    assertEquals(1, JSONObject(disk.content).length())

    disk.failOnWrite = null
    val retainedFailure =
      runCatching {
          active.execute("passenger-A", "feedback.submit", payload) { id ->
            assertEquals(acceptedKey, id) // failed clearing did not change the in-memory journal
            throw ApiError(403, "gate temporarily rejected")
          }
        }
        .exceptionOrNull()
    assertTrue(retainedFailure is ApiError)
    journal(disk, ids).execute("passenger-A", "feedback.submit", payload) { id ->
      assertEquals(acceptedKey, id) // and the same key survives process reconstruction
      "confirmed"
    }
    assertEquals(0, JSONObject(disk.content).length())

    val corrupted = runCatching { PendingRequestJournal(load = { "broken record" }, save = {}) }
    assertTrue(corrupted.exceptionOrNull() is PendingStorageException)
  }

  @Test
  fun currentTokenLegacyRecordMigratesWithoutInventingAKeyForAnUnavailableOldToken() = runBlocking {
    // Fixed fixtures from the previous production SHA256(token|action|payload) format.
    val legacyHash = "44915bd7ad88a8d61b8466ec6d36d1faedf13bbd25bf85eb87591338f44cefdb"
    val disk = Disk(JSONObject().put(legacyHash, "legacy-request").toString())
    val ids = Ids()
    val migrationFailure = runCatching {
      journal(disk, ids).execute(
        "passenger-A",
        "feedback.submit",
        payload,
        "legacy-current-token",
      ) { id ->
        assertEquals("legacy-request", id)
        assertFalse(JSONObject(disk.content).has(legacyHash)) // migrated before network dispatch
        throw ApiError(401, "relogin required")
      }
    }
    assertTrue(migrationFailure.exceptionOrNull() is ApiError)
    journal(disk, ids).execute("passenger-A", "feedback.submit", payload, "rotated-token") { id ->
      assertEquals("legacy-request", id)
    }
    assertEquals(0, ids.generated)

    val lostHash = "fa79b2f6282cbec397b221b652993268f1425f77340ab772f9e31b7b845b6043"
    val lostDisk = Disk(JSONObject().put(lostHash, "unrecoverable-old-token-request").toString())
    journal(lostDisk, ids).execute(
      "passenger-A",
      "feedback.submit",
      payload,
      "only-current-token-known",
    ) { id ->
      assertNotEquals("unrecoverable-old-token-request", id)
    }
    assertTrue(JSONObject(lostDisk.content).has(lostHash))
  }
}
