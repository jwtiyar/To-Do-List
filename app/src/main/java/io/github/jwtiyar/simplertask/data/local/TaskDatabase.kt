package io.github.jwtiyar.simplertask.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.local.dao.CategoryDao
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.dao.TaskDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE task ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'")
        database.execSQL("ALTER TABLE task ADD COLUMN notificationId INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No schema changes, just update the version to match identity hash
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE task ADD COLUMN isSaved INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE task ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE task ADD COLUMN recurrenceType TEXT")
        database.execSQL("ALTER TABLE task ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE task ADD COLUMN recurrenceEndDate INTEGER")
        database.execSQL("ALTER TABLE task ADD COLUMN parentTaskId INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create categories table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `category` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Add categoryId column to tasks table
        database.execSQL("ALTER TABLE task ADD COLUMN categoryId INTEGER")
        
        // Add index on categoryId for faster lookups
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_categoryId` ON `task` (`categoryId`)")

        // Insert default categories
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Work', -4484344, 0)")     // Blue
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Personal', -11419112, 0)") // Green
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Health', -769226, 0)")    // Red
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Learning', -6543440, 0)")  // Purple
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Shopping', -26624, 0)")   // Orange
        database.execSQL("INSERT INTO category (name, color, createdAt) VALUES ('Home', -13391360, 0)")    // Teal
    }
}

@Database(entities = [Task::class, Category::class], version = 6, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
