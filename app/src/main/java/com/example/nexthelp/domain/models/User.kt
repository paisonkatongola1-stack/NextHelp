package com.example.nexthelp.domain.models

import androidx.annotation.Keep

enum class UserRole {
    USER,
    SUPPORT_AGENT,
    SUPPORT_MANAGER,
    ADMIN
}

val User.canHandleTickets: Boolean
    get() = role in setOf(UserRole.SUPPORT_AGENT, UserRole.SUPPORT_MANAGER, UserRole.ADMIN)

@Keep
data class User(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.USER,
    val profileImageUrl: String? = null,
    val coverImageUrl: String? = null,
    val bio: String = "",
    val phoneNumber: String = "",
    val location: String = "",
    val createdAt: Long = 0L
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", UserRole.USER)
}
