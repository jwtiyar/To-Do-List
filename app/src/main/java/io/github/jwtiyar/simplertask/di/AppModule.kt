package io.github.jwtiyar.simplertask.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.data.local.dao.TaskDao
import io.github.jwtiyar.simplertask.data.local.dao.CategoryDao
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import javax.inject.Singleton

/**
 * Hilt module providing application-scoped dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTaskDatabase(@ApplicationContext context: Context): TaskDatabase {
        return TaskDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: TaskDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: TaskDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao, categoryDao: CategoryDao): TaskRepository {
        return TaskRepository(taskDao, categoryDao)
    }
}
