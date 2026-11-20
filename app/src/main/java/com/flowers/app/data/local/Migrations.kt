package com.flowers.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Міграція схеми Room 1 → 2 (TASK-025).
 * Лише додавання колонки й оновлення рядків ШІ-карток: жодних DROP/DELETE, тож рослини користувача
 * (`plants`, `care_events`) і каталог не зачіпаються.
 */
object Migrations {

    val MIGRATION_1_2_SQL: List<String> = listOf(
        // Нова колонка: false для всіх наявних рядків.
        "ALTER TABLE `catalog_plants` ADD COLUMN `isAiGenerated` INTEGER NOT NULL DEFAULT 0",
        // Картки, раніше згенеровані ШІ (id починається з `ai_`), позначаємо; токсичність від ШІ відкидаємо.
        "UPDATE `catalog_plants` SET `isAiGenerated` = 1, `toxicToPets` = 0 WHERE `id` LIKE 'ai\\_%' ESCAPE '\\'"
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
        }
    }
}
