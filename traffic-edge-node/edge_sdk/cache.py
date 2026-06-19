import logging
import os
import sqlite3
import threading
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


class EventCache:
    UPLOAD_STATUS_PENDING = 0
    UPLOAD_STATUS_UPLOADING = 1
    UPLOAD_STATUS_SUCCESS = 2
    UPLOAD_STATUS_FAILED = 3

    def __init__(self, db_path: str = "./edge_cache.db"):
        self.db_path = db_path
        self._lock = threading.Lock()
        self._init_db()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        db_dir = os.path.dirname(self.db_path)
        if db_dir:
            os.makedirs(db_dir, exist_ok=True)
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    CREATE TABLE IF NOT EXISTS offline_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_uuid TEXT PRIMARY KEY,
                        event_type TEXT NOT NULL,
                        event_time TEXT NOT NULL,
                        event_data TEXT NOT NULL,
                        snapshot_path TEXT,
                        video_path TEXT,
                        upload_status INTEGER DEFAULT 0,
                        retry_count INTEGER DEFAULT 0,
                        error_message TEXT,
                        created_at TEXT NOT NULL,
                        uploaded_at TEXT
                    )
                    """
                )
                cursor.execute(
                    "CREATE INDEX IF NOT EXISTS idx_upload_status ON offline_events(upload_status)"
                )
                cursor.execute(
                    "CREATE INDEX IF NOT EXISTS idx_created_at ON offline_events(created_at)"
                )
                conn.commit()
                logger.info(f"Event cache initialized at: {self.db_path}")
            except Exception as e:
                logger.error(f"Failed to initialize event cache: {e}", exc_info=True)
                conn.rollback()
                raise
            finally:
                conn.close()

    def init_db(self):
        self._init_db()

    def add_event(
        self,
        event_uuid: str,
        event_type: str,
        event_time: str,
        event_data: str,
        snapshot_path: Optional[str] = None,
        video_path: Optional[str] = None,
    ) -> bool:
        now = datetime.now().isoformat()
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    INSERT OR IGNORE INTO offline_events
                    (event_uuid, event_type, event_time, event_data, snapshot_path, video_path,
                     upload_status, retry_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                    """,
                    (
                        event_uuid,
                        event_type,
                        event_time,
                        event_data,
                        snapshot_path,
                        video_path,
                        self.UPLOAD_STATUS_PENDING,
                        now,
                    ),
                )
                conn.commit()
                if cursor.rowcount > 0:
                    logger.debug(
                        f"Event cached: uuid={event_uuid}, type={event_type}"
                    )
                    return True
                else:
                    logger.warning(f"Event already exists in cache: {event_uuid}")
                    return False
            except Exception as e:
                logger.error(f"Failed to add event to cache: {e}", exc_info=True)
                conn.rollback()
                return False
            finally:
                conn.close()

    def get_pending_events(self, limit: int = 100) -> List[Dict[str, Any]]:
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    SELECT * FROM offline_events
                    WHERE upload_status = ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    """,
                    (self.UPLOAD_STATUS_PENDING, limit),
                )
                rows = cursor.fetchall()
                events = [dict(row) for row in rows]
                logger.debug(f"Fetched {len(events)} pending events from cache")
                return events
            except Exception as e:
                logger.error(f"Failed to get pending events: {e}", exc_info=True)
                return []
            finally:
                conn.close()

    def mark_uploading(self, event_uuid: str) -> bool:
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    UPDATE offline_events
                    SET upload_status = ?
                    WHERE event_uuid = ? AND upload_status = ?
                    """,
                    (
                        self.UPLOAD_STATUS_UPLOADING,
                        event_uuid,
                        self.UPLOAD_STATUS_PENDING,
                    ),
                )
                conn.commit()
                success = cursor.rowcount > 0
                if success:
                    logger.debug(f"Event marked as uploading: {event_uuid}")
                return success
            except Exception as e:
                logger.error(f"Failed to mark event as uploading: {e}", exc_info=True)
                conn.rollback()
                return False
            finally:
                conn.close()

    def mark_success(self, event_uuid: str) -> bool:
        now = datetime.now().isoformat()
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    UPDATE offline_events
                    SET upload_status = ?, uploaded_at = ?
                    WHERE event_uuid = ?
                    """,
                    (self.UPLOAD_STATUS_SUCCESS, now, event_uuid),
                )
                conn.commit()
                success = cursor.rowcount > 0
                if success:
                    logger.info(f"Event marked as upload success: {event_uuid}")
                return success
            except Exception as e:
                logger.error(f"Failed to mark event as success: {e}", exc_info=True)
                conn.rollback()
                return False
            finally:
                conn.close()

    def mark_failed(self, event_uuid: str, error_msg: str) -> bool:
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    UPDATE offline_events
                    SET upload_status = ?, error_message = ?, retry_count = retry_count + 1
                    WHERE event_uuid = ?
                    """,
                    (self.UPLOAD_STATUS_FAILED, error_msg, event_uuid),
                )
                conn.commit()
                success = cursor.rowcount > 0
                if success:
                    logger.warning(
                        f"Event marked as upload failed: {event_uuid}, error: {error_msg}"
                    )
                return success
            except Exception as e:
                logger.error(f"Failed to mark event as failed: {e}", exc_info=True)
                conn.rollback()
                return False
            finally:
                conn.close()

    def reset_failed_events(self, max_retry: int = 5) -> int:
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    UPDATE offline_events
                    SET upload_status = ?
                    WHERE upload_status = ? AND retry_count < ?
                    """,
                    (
                        self.UPLOAD_STATUS_PENDING,
                        self.UPLOAD_STATUS_FAILED,
                        max_retry,
                    ),
                )
                conn.commit()
                count = cursor.rowcount
                if count > 0:
                    logger.info(f"Reset {count} failed events for retry")
                return count
            except Exception as e:
                logger.error(f"Failed to reset failed events: {e}", exc_info=True)
                conn.rollback()
                return 0
            finally:
                conn.close()

    def get_queue_size(self) -> int:
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    SELECT COUNT(*) FROM offline_events
                    WHERE upload_status IN (?, ?)
                    """,
                    (self.UPLOAD_STATUS_PENDING, self.UPLOAD_STATUS_FAILED),
                )
                count = cursor.fetchone()[0]
                return count
            except Exception as e:
                logger.error(f"Failed to get queue size: {e}", exc_info=True)
                return 0
            finally:
                conn.close()

    def cleanup_old_events(self, days: int = 7) -> int:
        cutoff = (datetime.now() - timedelta(days=days)).isoformat()
        with self._lock:
            conn = self._get_connection()
            try:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    DELETE FROM offline_events
                    WHERE upload_status = ? AND uploaded_at < ?
                    """,
                    (self.UPLOAD_STATUS_SUCCESS, cutoff),
                )
                conn.commit()
                count = cursor.rowcount
                if count > 0:
                    logger.info(f"Cleaned up {count} old events (older than {days} days)")
                return count
            except Exception as e:
                logger.error(f"Failed to cleanup old events: {e}", exc_info=True)
                conn.rollback()
                return 0
            finally:
                conn.close()
