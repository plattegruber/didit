package com.plattegruber.didit.core.data.di

import android.content.Context
import androidx.room.Room
import com.plattegruber.didit.core.data.RoomHabitRepository
import com.plattegruber.didit.core.data.db.DiditDatabase
import com.plattegruber.didit.core.domain.repository.HabitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindHabitRepository(repository: RoomHabitRepository): HabitRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): DiditDatabase =
            Room.databaseBuilder(context, DiditDatabase::class.java, DiditDatabase.NAME).build()
    }
}
