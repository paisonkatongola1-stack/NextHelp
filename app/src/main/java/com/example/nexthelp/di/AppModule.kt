package com.example.nexthelp.di

import com.example.nexthelp.data.repository.AuthRepositoryImpl
import com.example.nexthelp.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): AuthRepository = AuthRepositoryImpl(firebaseAuth, firestore)

    @Provides
    @Singleton
    fun provideTicketRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth
    ): com.example.nexthelp.domain.repository.TicketRepository = 
        com.example.nexthelp.data.repository.TicketRepositoryImpl(firestore, firebaseAuth)
}
