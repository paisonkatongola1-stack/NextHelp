package com.example.nexthelp.core.util

import com.example.nexthelp.BuildConfig

data class AppConfig(
    val adminEmails: Set<String>,
    val devAdminEmail: String?,
    val devAdminPassword: String?
) {
    fun isAdminEmail(email: String?): Boolean =
        email != null && adminEmails.any { it.equals(email.trim(), ignoreCase = true) }

    val isDevLoginEnabled: Boolean
        get() = BuildConfig.DEBUG && !devAdminEmail.isNullOrBlank() && !devAdminPassword.isNullOrBlank()

    companion object {
        fun fromBuildConfig() = AppConfig(
            adminEmails = BuildConfig.ADMIN_EMAILS.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            devAdminEmail = BuildConfig.DEV_ADMIN_EMAIL.takeIf { it.isNotBlank() },
            devAdminPassword = BuildConfig.DEV_ADMIN_PASSWORD.takeIf { it.isNotBlank() }
        )
    }
}
