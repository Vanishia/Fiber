package com.bird.fiber.di

import android.content.ContentResolver
import android.content.Context
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.FileRepositoryImpl
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryDao
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.repository.FileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt依赖注入模块
 *
 * 这里定义了如何提供Repository实例和数据库实例
 * 如果需要更换Repository实现，只需要修改这里的绑定
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供数据库实例
     */
    @Provides
    @Singleton
    fun provideFiberDatabase(
        @ApplicationContext context: Context
    ): FiberDatabase {
        return FiberDatabase.getInstance(context)
    }

    /**
     * 提供 LibraryDao
     */
    @Provides
    @Singleton
    fun provideLibraryDao(database: FiberDatabase): LibraryDao {
        return database.libraryDao()
    }

    /**
     * 提供 MarkdownFileDao
     */
    @Provides
    @Singleton
    fun provideMarkdownFileDao(database: FiberDatabase): MarkdownFileDao {
        return database.markdownFileDao()
    }

    /**
     * 提供 LibraryRepository
     */
    @Provides
    @Singleton
    fun provideLibraryRepository(libraryDao: LibraryDao): LibraryRepository {
        return LibraryRepository(libraryDao)
    }

    /**
     * 提供 ContentResolver
     */
    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * 提供FileRepository实现
     */
    @Binds
    @Singleton
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository
}
