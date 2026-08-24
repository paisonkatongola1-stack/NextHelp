package com.example.nexthelp.di

import com.example.nexthelp.core.util.AppConfig
import com.example.nexthelp.core.util.ApplicationScope
import com.example.nexthelp.data.repository.AuthRepositoryImpl
import com.example.nexthelp.data.repository.TicketRepositoryImpl
import com.example.nexthelp.domain.repository.AuthRepository
import com.example.nexthelp.domain.repository.TicketRepository
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext app: Context): CredentialManager =
        CredentialManager.create(app)

    @Provides
    @Singleton
    fun provideAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository = impl

    @Provides
    @Singleton
    fun provideTicketRepository(
        impl: TicketRepositoryImpl
    ): TicketRepository = impl
}
