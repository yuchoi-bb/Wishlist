package com.wishlist.app.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Auth, signing in with the same Google account already used for Drive backup so
 * the user only picks an account once. Firestore security rules key every item on this uid, so
 * signing in is what scopes a device to "your" data instead of anyone else's.
 */
class AuthManager(context: Context) {
    // Null before google-services.json is added: there's no default FirebaseApp yet, and
    // FirebaseAuth.getInstance() throws IllegalStateException in that case.
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val _currentUser = MutableStateFlow(auth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    /**
     * The OAuth "Web client ID" the google-services plugin generates once Google sign-in is
     * enabled in the Firebase console. Looked up by resource name, not R.string.default_web_client_id,
     * so this class still compiles and the app still runs before google-services.json is added.
     */
    val webClientId: String? = context.resources
        .getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }
        ?.let { context.getString(it) }

    val isConfigured: Boolean get() = auth != null && webClientId != null

    init {
        auth?.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): FirebaseUser? {
        val authInstance = auth ?: return null
        val idToken = account.idToken ?: return null
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return authInstance.signInWithCredential(credential).await().user
    }

    fun signOut() {
        auth?.signOut()
    }

    /**
     * One Google sign-in flow covering both concerns: Drive.appdata scope (manual backup) and an
     * ID token (Firebase Auth), so the user only picks an account once.
     */
    fun signInClient(context: Context): GoogleSignInClient {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        webClientId?.let { builder.requestIdToken(it) }
        return GoogleSignIn.getClient(context, builder.build())
    }
}
