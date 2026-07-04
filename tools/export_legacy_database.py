#!/usr/bin/env python3
"""
Export LianYu legacy/master SQLite database content to JSON.

This is an offline, standalone operator tool. It reads a copied database file
from a workstation/server and writes a JSON snapshot. It does not integrate
with the Android app UI and does not expose any in-app export surface.

Default safety: token/API-key-like fields are redacted. Pass --include-secrets
only when doing a controlled local migration and handling the output securely.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

LEGACY_TABLES = [
    "companions",
    "chat_messages",
    "api_configs",
    "memory_entries",
    "temp_memory",
    "chat_groups",
    "group_messages",
]

SECRET_COLUMNS = {
    "apiKey",
    "api_key",
    "authToken",
    "auth_token",
    "refreshToken",
    "refresh_token",
    "botToken",
    "bot_token",
    "contextToken",
    "context_token",
}


def connect_readonly(db_path: Path) -> sqlite3.Connection:
    if not db_path.exists():
        raise FileNotFoundError(f"database not found: {db_path}")
    uri = f"file:{db_path.resolve()}?mode=ro"
    conn = sqlite3.connect(uri, uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def get_schema_version(conn: sqlite3.Connection) -> int:
    return int(conn.execute("PRAGMA user_version").fetchone()[0])


def table_exists(conn: sqlite3.Connection, table: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        (table,),
    ).fetchone()
    return row is not None


def column_names(conn: sqlite3.Connection, table: str) -> list[str]:
    return [str(row[1]) for row in conn.execute(f"PRAGMA table_info(`{table}`)").fetchall()]


def redact_row(row: dict[str, Any], include_secrets: bool) -> dict[str, Any]:
    if include_secrets:
        return row
    return {
        key: ("[REDACTED]" if key in SECRET_COLUMNS and value not in (None, "") else value)
        for key, value in row.items()
    }


def export_table(conn: sqlite3.Connection, table: str, include_secrets: bool) -> list[dict[str, Any]]:
    if not table_exists(conn, table):
        return []
    rows = conn.execute(f"SELECT * FROM `{table}`").fetchall()
    return [redact_row(dict(row), include_secrets) for row in rows]


def export_legacy_database(
    db_path: str | Path,
    output_path: str | Path,
    *,
    include_secrets: bool = False,
    tables: list[str] | None = None,
) -> dict[str, Any]:
    db_path = Path(db_path)
    output_path = Path(output_path)
    requested_tables = tables or LEGACY_TABLES

    with connect_readonly(db_path) as conn:
        schema_version = get_schema_version(conn)
        table_payload = {
            table: export_table(conn, table, include_secrets)
            for table in requested_tables
        }
        schema_payload = {
            table: column_names(conn, table)
            for table in requested_tables
            if table_exists(conn, table)
        }

    payload = {
        "metadata": {
            "tool": "export_legacy_database.py",
            "source": str(db_path),
            "schema_version": schema_version,
            "exported_at": datetime.now(timezone.utc).isoformat(),
            "include_secrets": include_secrets,
            "format": "lianyu-legacy-json-v1",
            "supported_legacy_schema": "master/v9 and compatible earlier SQLite Room schemas",
        },
        "schema": schema_payload,
        "tables": table_payload,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )

    return {
        "output": str(output_path),
        "schema_version": schema_version,
        "tables": {table: len(rows) for table, rows in table_payload.items()},
        "include_secrets": include_secrets,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a copied LianYu legacy/master SQLite database to JSON."
    )
    parser.add_argument("database", type=Path, help="Path to copied lianyu_database SQLite file")
    parser.add_argument("--output", "-o", type=Path, required=True, help="Output JSON path")
    parser.add_argument(
        "--include-secrets",
        action="store_true",
        help="Include API keys/tokens instead of redacting them. Use only for controlled local migration.",
    )
    parser.add_argument(
        "--table",
        action="append",
        choices=LEGACY_TABLES,
        help="Export only this table. Can be repeated. Defaults to all legacy tables.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    summary = export_legacy_database(
        args.database,
        args.output,
        include_secrets=args.include_secrets,
        tables=args.table,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
