package com.example.nexthelp.domain.models

enum class UserRole {
    USER,
    SUPPORT_AGENT,
    SUPPORT_MANAGER,
    ADMIN
}

data class User(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.USER,
    val profileImageUrl: String? = null
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", UserRole.USER)
}
