package com.example.nexthelp.data.repository

import com.example.nexthelp.core.util.AppConfig
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
    private val appConfig: AppConfig
) : AuthRepository {

    private var devSessionActive = false
    private var userProfileListener: ListenerRegistration? = null

    init {
        firebaseAuth.addAuthStateListener { auth ->
            if (devSessionActive) return@addAuthStateListener

            val fbUser = auth.currentUser
            sessionManager.update(fbUser?.toDomainUser())
            if (fbUser != null) observeUserProfile(fbUser.uid) else clearUserProfileListener()
        }
    }

    /**
     * Keeps the session user in sync with the `users/{uid}` Firestore document so
     * profile edits (name, bio, photos) propagate live across the app.
     */
    private fun observeUserProfile(uid: String) {
        clearUserProfileListener()
        userProfileListener = firestore.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (devSessionActive) return@addSnapshotListener
                val doc = snapshot?.toObject(User::class.java)
                if (snapshot != null && !snapshot.exists()) {
                    // Legacy account without a profile document: create it from Auth.
                    firebaseAuth.currentUser?.let { fb ->
                        firestore.collection(USERS_COLLECTION).document(fb.uid)
                            .set(fb.toDomainUser(), SetOptions.merge())
                    }
                    return@addSnapshotListener
                }
                if (doc == null) return@addSnapshotListener

                val base = firebaseAuth.currentUser?.toDomainUser() ?: doc
                sessionManager.update(
                    base.copy(
                        fullName = doc.fullName.ifBlank { base.fullName },
                        role = doc.role,
                        bio = doc.bio,
                        phoneNumber = doc.phoneNumber,
                        location = doc.location,
                        createdAt = doc.createdAt,
                        profileImageUrl = doc.profileImageUrl ?: base.profileImageUrl,
                        coverImageUrl = doc.coverImageUrl
                    )
                )
            }
    }

    private fun clearUserProfileListener() {
        userProfileListener?.remove()
        userProfileListener = null
    }

    override val currentUser get() = sessionManager.currentUser

    override suspend fun loginWithEmail(email: String, password: String): Resource<User> {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        // Dev-only login so the team can work without Firebase Auth being fully configured.
        // Credentials come from local.properties and are only present in debug builds.
        if (appConfig.isDevLoginEnabled &&
            trimmedEmail.equals(appConfig.devAdminEmail, ignoreCase = true) &&
            trimmedPassword == appConfig.devAdminPassword
        ) {
            val adminUser = User(
                id = DEV_USER_ID,
                fullName = "Administrator",
                email = trimmedEmail,
                role = UserRole.ADMIN
            )
            devSessionActive = true
            sessionManager.update(adminUser)
            return Resource.Success(adminUser)
        }

        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            val user = result.user!!.toDomainUser()
            devSessionActive = false
            sessionManager.update(user)
            observeUserProfile(user.id)
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.friendlyMessage(fallback = "Login failed. Please try again."))
        }
    }

    override suspend fun registerWithEmail(email: String, fullName: String, password: String): Resource<User> {
        val trimmedEmail = email.trim()

        if (appConfig.isAdminEmail(trimmedEmail) && appConfig.isDevLoginEnabled) {
            val adminUser = User(DEV_USER_ID, fullName.ifBlank { "Administrator" }, trimmedEmail, UserRole.ADMIN)
            devSessionActive = true
            sessionManager.update(adminUser)
            return Resource.Success(adminUser)
        }

        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val role = if (appConfig.isAdminEmail(trimmedEmail)) UserRole.ADMIN else UserRole.USER
            val user = User(
                id = result.user!!.uid,
                fullName = fullName.trim(),
                email = trimmedEmail,
                role = role,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection(USERS_COLLECTION).document(user.id).set(user).await()
            devSessionActive = false
            sessionManager.update(user)
            observeUserProfile(user.id)
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.friendlyMessage(fallback = "Registration failed. Please try again."))
        }
    }

    override suspend fun updateDisplayName(fullName: String): Resource<Unit> {
        val trimmed = fullName.trim()
        if (trimmed.isEmpty()) return Resource.Error("Name cannot be empty")

        val session = sessionManager.currentUser.value ?: return Resource.Error("You are signed out.")
        if (session.id == DEV_USER_ID) {
            // The dev session has no backing Firebase profile to update.
            sessionManager.update(session.copy(fullName = trimmed))
            return Resource.Success(Unit)
        }

        return try {
            firebaseAuth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmed)
                    .build()
            )?.await()
            firestore.collection(USERS_COLLECTION).document(session.id)
                .set(mapOf(FULL_NAME_FIELD to trimmed), SetOptions.merge())
                .await()
            sessionManager.update(session.copy(fullName = trimmed))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update profile")
        }
    }

    override suspend fun updateProfileDetails(
        bio: String,
        phoneNumber: String,
        location: String
    ): Resource<Unit> {
        val session = sessionManager.currentUser.value ?: return Resource.Error("You are signed out.")
        val updated = session.copy(
            bio = bio.trim(),
            phoneNumber = phoneNumber.trim(),
            location = location.trim()
        )

        if (session.id == DEV_USER_ID) {
            sessionManager.update(updated)
            return Resource.Success(Unit)
        }

        return try {
            firestore.collection(USERS_COLLECTION).document(session.id)
                .set(
                    mapOf(
                        BIO_FIELD to updated.bio,
                        PHONE_FIELD to updated.phoneNumber,
                        LOCATION_FIELD to updated.location
                    ),
                    SetOptions.merge()
                )
                .await()
            sessionManager.update(updated)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update profile")
        }
    }

    override suspend fun logout() {
        devSessionActive = false
        clearUserProfileListener()
        sessionManager.update(null)
        firebaseAuth.signOut()
    }

    override suspend fun loginWithGoogle(idToken: String): Resource<User> {
        if (idToken.isBlank()) return Resource.Error("Missing Google credential. Please try again.")

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val fbUser = result.user ?: return Resource.Error("Sign-in failed. Please try again.")
            devSessionActive = false

            // First-time Google users get a profile document created for them.
            val existingDoc = firestore.collection(USERS_COLLECTION).document(fbUser.uid).get().await()
            if (!existingDoc.exists()) {
                firestore.collection(USERS_COLLECTION).document(fbUser.uid)
                    .set(fbUser.toDomainUser().copy(createdAt = System.currentTimeMillis()))
                    .await()
            }

            sessionManager.update(fbUser.toDomainUser())
            observeUserProfile(fbUser.uid)
            Resource.Success(fbUser.toDomainUser())
        } catch (e: Exception) {
            Resource.Error(e.friendlyMessage(fallback = "Google sign-in failed. Please try again."))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.friendlyMessage(fallback = "Failed to send reset email"))
        }
    }

    private fun FirebaseUser.toDomainUser(): User = User(
        id = uid,
        fullName = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@") ?: "User",
        email = email ?: "",
        role = if (appConfig.isAdminEmail(email)) UserRole.ADMIN else UserRole.USER,
        profileImageUrl = photoUrl?.toString()
    )

    private fun Exception.friendlyMessage(fallback: String): String = when (this) {
        is FirebaseAuthInvalidUserException -> "No account found with this email."
        is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
        is FirebaseAuthUserCollisionException -> "An account with this email already exists."
        is FirebaseAuthWeakPasswordException -> reason ?: "Password is too weak. Use at least 6 characters."
        is FirebaseNetworkException -> "Network error. Please check your connection."
        is FirebaseAuthException -> localizedMessage ?: fallback
        else -> localizedMessage ?: fallback
    }

    private companion object {
        const val DEV_USER_ID = "admin-dev-id"
        const val USERS_COLLECTION = "users"
        const val FULL_NAME_FIELD = "fullName"
        const val BIO_FIELD = "bio"
        const val PHONE_FIELD = "phoneNumber"
        const val LOCATION_FIELD = "location"
    }
}
