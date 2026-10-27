package com.example.data.model

/** An account listed in the cloud `users` collection, as offered when assigning a project. */
data class RegisteredUser(val uid: String, val name: String, val email: String = "", val role: String = "")
