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

    /**
     * Міграція Room 2 → 3 (TASK-053).
     * Адитивне додавання полів персональної автономної картки Flowers 2.0 до таблиці `plants`.
     * Жодних DROP TABLE чи деструктивних дій: усі наявні рослини, фото та зв'язки зберігаються 100%.
     */
    val MIGRATION_2_3_SQL: List<String> = listOf(
        "ALTER TABLE `plants` ADD COLUMN `speciesName` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `botanicalName` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `description` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `wateringAdvice` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `soilType` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `toxicityInfo` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `aiConfidence` TEXT DEFAULT NULL",
        "ALTER TABLE `plants` ADD COLUMN `isAiGenerated` INTEGER NOT NULL DEFAULT 0"
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
        }
    }
}
