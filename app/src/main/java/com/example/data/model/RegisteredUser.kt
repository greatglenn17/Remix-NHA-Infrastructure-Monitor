package com.example.data.model

/** An account listed in the cloud `users` collection, as offered when assigning a project. */
data class RegisteredUser(
    val uid: String,
    val name: String,
    val email: String = "",
    val role: String = "",
    // The Super Admin's "See all projects" switch (users/{uid}.seesAllProjects).
    val seesAllProjects: Boolean = false,
    // users/{uid}.approved; a profile from before approvals has none and counts as approved.
    val approved: Boolean = true
)
