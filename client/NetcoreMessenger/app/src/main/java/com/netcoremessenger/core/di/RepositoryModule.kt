package com.netcoremessenger.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Все Repository аннотированы @Singleton + @Inject constructor,
    // Hilt сам их подхватит. Модуль оставлен на случай будущих биндингов.
}
