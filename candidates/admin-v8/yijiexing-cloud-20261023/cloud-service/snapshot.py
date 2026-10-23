"""Single-object, generation-conditional SQLite persistence for a small demo.

SQLite always runs on the container's local filesystem, never on a GCS mount.
Every response that changed SQL is withheld until a conditional upload succeeds.
"""
import hashlib
import os
import pathlib
import sqlite3
from dataclasses import dataclass


class SnapshotUnavailable(Exception):
    pass


class SnapshotConflict(SnapshotUnavailable):
    pass


@dataclass(frozen=True)
class ObjectVersion:
    generation: int
    data: bytes | None


class GCSObject:
    """Uses attached workload identity; no downloaded service-account key."""
    def __init__(self, bucket, object_name):
        from google.cloud import storage
        self.bucket = storage.Client().bucket(bucket)
        self.object_name = object_name

    def read(self, known_generation=None):
        from google.api_core.exceptions import NotFound, PreconditionFailed
        try:
            blob = self.bucket.blob(self.object_name)
            blob.reload(timeout=8, retry=None)
            generation = int(blob.generation)
            if generation == known_generation:
                return ObjectVersion(generation, None)
            data = blob.download_as_bytes(if_generation_match=generation, timeout=15, retry=None)
            return ObjectVersion(generation, data)
        except NotFound:
            # Only a genuinely absent metadata object means an uninitialized demo.
            # A deletion between metadata and content is an unavailable snapshot.
            if 'generation' in locals():
                raise SnapshotUnavailable('snapshot changed while downloading') from None
            return ObjectVersion(0, None)
        except PreconditionFailed:
            raise SnapshotConflict('snapshot changed while downloading') from None
        except Exception as exc:
            raise SnapshotUnavailable('cloud snapshot read unavailable') from exc

    def write(self, data, expected_generation):
        from google.api_core.exceptions import PreconditionFailed
        try:
            blob = self.bucket.blob(self.object_name)
            blob.cache_control = 'no-store'
            blob.upload_from_string(data, content_type='application/vnd.sqlite3',
                                    if_generation_match=expected_generation, timeout=20, retry=None)
            return int(blob.generation)
        except PreconditionFailed:
            raise SnapshotConflict('another request committed first') from None
        except Exception as exc:
            # An uncertain upload may have committed. Do not acknowledge it and
            # invalidate all local state; the next request reads the authority.
            raise SnapshotUnavailable('cloud snapshot write unavailable') from exc


class SnapshotCoordinator:
    def __init__(self, remote, db_path, service_factory, max_bytes=32 * 1024 * 1024):
        self.remote = remote
        self.path = pathlib.Path(db_path)
        self.factory = service_factory
        self.max_bytes = max_bytes
        self.service = None
        self.generation = None
        self.persisted_digest = None
        self.ever_existed = False
        self.change_count = 0

    def close(self):
        if self.service is not None:
            self.service.close()
            self.service = None

    def invalidate(self):
        self.close()
        self.generation = None
        self.persisted_digest = None
        for suffix in ('', '-wal', '-shm'):
            try:
                pathlib.Path(str(self.path) + suffix).unlink()
            except FileNotFoundError:
                pass

    def _restore(self, data):
        if len(data) > self.max_bytes or not data.startswith(b'SQLite format 3\0'):
            raise SnapshotUnavailable('invalid or oversized snapshot')
        self.close()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        os.chmod(self.path.parent, 0o700)
        for suffix in ('-wal', '-shm'):
            try:
                pathlib.Path(str(self.path) + suffix).unlink()
            except FileNotFoundError:
                pass
        tmp = self.path.with_suffix('.download')
        tmp.write_bytes(data)
        os.chmod(tmp, 0o600)
        check = sqlite3.connect(str(tmp))
        try:
            if check.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
                raise SnapshotUnavailable('snapshot integrity check failed')
        finally:
            check.close()
        tmp.replace(self.path)
        self.service = self.factory(self.path)

    def begin(self):
        version = self.remote.read(self.generation)
        if version.generation == 0:
            if self.ever_existed:
                raise SnapshotUnavailable('existing snapshot disappeared; refusing reseed')
            if self.service is None:
                self.path.parent.mkdir(parents=True, exist_ok=True)
                os.chmod(self.path.parent, 0o700)
                for suffix in ('', '-wal', '-shm'):
                    try:
                        pathlib.Path(str(self.path) + suffix).unlink()
                    except FileNotFoundError:
                        pass
                self.service = self.factory(self.path)
            self.generation = 0
            self.persisted_digest = None
        else:
            self.ever_existed = True
            if version.data is not None:
                self._restore(version.data)
                self.persisted_digest = hashlib.sha256(self.snapshot()).digest()
            elif self.service is None:
                raise SnapshotUnavailable('snapshot body missing')
            self.generation = version.generation
        self.change_count = self.service.db.total_changes
        return self.service

    def snapshot(self):
        # backup() includes committed WAL contents and yields a complete standalone
        # database. Copying just the live .sqlite file would lose WAL transactions.
        target = sqlite3.connect(':memory:')
        try:
            self.service.db.backup(target)
            return target.serialize()
        finally:
            target.close()

    def finish(self):
        self.service.db.commit()
        if self.generation != 0 and self.service.db.total_changes == self.change_count:
            return
        data = self.snapshot()
        if len(data) > self.max_bytes:
            raise SnapshotUnavailable('demo data quota exceeded; admin export required')
        digest = hashlib.sha256(data).digest()
        if digest == self.persisted_digest:
            return
        self.generation = self.remote.write(data, self.generation)
        self.persisted_digest = digest
        self.ever_existed = True
