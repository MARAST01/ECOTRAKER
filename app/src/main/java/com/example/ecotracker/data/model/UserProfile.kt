package com.example.ecotracker.data.model

import androidx.annotation.Keep

// Firestore requires a public no-arg constructor and mutable fields for reflection-based deserialization.
@Keep
data class UserProfile(
    var uid: String? = null,
    var fullName: String? = null,
    var phone: String? = null,
    var email: String? = null,
    var createdAt: Long? = null
) {
    // Explicit no-arg constructor for Firestore (even though defaults exist)
    constructor() : this(null, null, null, null, null)
}
