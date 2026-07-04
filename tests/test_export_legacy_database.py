#!/usr/bin/env python3
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from export_legacy_database import export_legacy_database


class LegacyDatabaseExportTest(unittest.TestCase):
    def create_master_v9_database(self, db_path: Path) -> None:
        conn = sqlite3.connect(db_path)
        try:
            conn.executescript(
                """
                CREATE TABLE companions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    avatarUrl TEXT,
                    age INTEGER,
                    personality TEXT NOT NULL DEFAULT '',
                    backstory TEXT,
                    speakingStyle TEXT,
                    tags TEXT,
                    rawPrompt TEXT,
                    systemPrompt TEXT,
                    intimacy INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    companionId INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    isFromUser INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL DEFAULT 0,
                    type TEXT NOT NULL DEFAULT 'TEXT'
                );
                CREATE TABLE api_configs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    provider TEXT NOT NULL,
                    name TEXT NOT NULL DEFAULT '',
                    apiKey TEXT NOT NULL,
                    baseUrl TEXT NOT NULL,
                    model TEXT NOT NULL,
                    temperature REAL NOT NULL DEFAULT 0.7,
                    maxTokens INTEGER,
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    connectionTested INTEGER NOT NULL DEFAULT 0,
                    connectionTestedAt INTEGER NOT NULL DEFAULT 0,
                    latencyMs INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE memory_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    companionId INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'FACT',
                    importance REAL NOT NULL DEFAULT 0.5,
                    context TEXT NOT NULL DEFAULT '',
                    accessCount INTEGER NOT NULL DEFAULT 1,
                    timestamp INTEGER NOT NULL DEFAULT 0,
                    lastAccessed INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE temp_memory (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    companionId INTEGER NOT NULL,
                    userInput TEXT NOT NULL,
                    botResponse TEXT NOT NULL,
                    timestamp INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE chat_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    avatarUrl TEXT,
                    companionIds TEXT NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE group_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    companionId INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL DEFAULT 0
                );
                PRAGMA user_version = 9;
                """
            )
            conn.execute("INSERT INTO companions (name, personality, intimacy, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)", ("真昼", "温柔", 12, 1000, 2000))
            conn.execute("INSERT INTO chat_messages (companionId, content, isFromUser, timestamp, type) VALUES (?, ?, ?, ?, ?)", (1, "你好，草莓蛋糕", 1, 1700000000001, "TEXT"))
            conn.execute("INSERT INTO api_configs (provider, name, apiKey, baseUrl, model, isEnabled, latencyMs) VALUES (?, ?, ?, ?, ?, ?, ?)", ("OPENAI", "prod", "sk-secret", "https://api.example/v1/", "gpt", 1, 23))
            conn.execute("INSERT INTO memory_entries (companionId, content, category, importance, context, timestamp) VALUES (?, ?, ?, ?, ?, ?)", (1, "用户喜欢草莓", "PREFERENCE", 0.8, "晚餐", 1700000000100))
            conn.execute("INSERT INTO temp_memory (companionId, userInput, botResponse, timestamp) VALUES (?, ?, ?, ?)", (1, "今天吃什么", "蛋包饭", 1700000000200))
            conn.execute("INSERT INTO chat_groups (name, companionIds, createdAt, updatedAt) VALUES (?, ?, ?, ?)", ("测试群", "1", 3000, 4000))
            conn.execute("INSERT INTO group_messages (groupId, companionId, content, timestamp) VALUES (?, ?, ?, ?)", (1, 1, "群消息", 1700000000300))
            conn.commit()
        finally:
            conn.close()

    def test_exports_master_v9_database_to_json_with_redacted_api_keys_by_default(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "lianyu_database"
            out_path = Path(tmp) / "export.json"
            self.create_master_v9_database(db_path)

            summary = export_legacy_database(db_path, out_path)

            data = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertEqual(summary["tables"]["companions"], 1)
            self.assertEqual(data["metadata"]["schema_version"], 9)
            self.assertEqual(data["tables"]["companions"][0]["name"], "真昼")
            self.assertEqual(data["tables"]["chat_messages"][0]["content"], "你好，草莓蛋糕")
            self.assertEqual(data["tables"]["api_configs"][0]["apiKey"], "[REDACTED]")
            self.assertEqual(data["tables"]["group_messages"][0]["content"], "群消息")

    def test_can_export_api_keys_when_explicitly_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "lianyu_database"
            out_path = Path(tmp) / "export.json"
            self.create_master_v9_database(db_path)

            export_legacy_database(db_path, out_path, include_secrets=True)

            data = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertEqual(data["tables"]["api_configs"][0]["apiKey"], "sk-secret")


if __name__ == "__main__":
    unittest.main()
