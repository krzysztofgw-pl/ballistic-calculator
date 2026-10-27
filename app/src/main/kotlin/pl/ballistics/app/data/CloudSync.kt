package pl.ballistics.app.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface SyncState {
    /** This build has no Firebase project configured. */
    data object Unavailable : SyncState
    data object SignedOut : SyncState
    data class SignedIn(val email: String?, val error: String? = null) : SyncState
}

/**
 * Keeps all profiles in one Firestore document per user (users/{uid}) holding
 * the same JSON as the export file. Whole-document last-write-wins by
 * [AppData.modifiedAt] is enough for one person with a few phones; Firestore
 * queues writes while offline.
 */
class CloudSync(private val context: Context, private val onRemote: (AppData) -> Unit) {
    private val available = FirebaseApp.getApps(context).isNotEmpty()
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val _state = MutableStateFlow<SyncState>(if (available) SyncState.SignedOut else SyncState.Unavailable)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        if (available) {
            auth.addAuthStateListener { a ->
                val user = a.currentUser
                listener?.remove()
                listener = null
                if (user == null) {
                    _state.value = SyncState.SignedOut
                } else {
                    _state.value = SyncState.SignedIn(user.email)
                    listen(user.uid)
                }
            }
        }
    }

    private fun listen(uid: String) {
        listener = db.collection(USERS).document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _state.value = SyncState.SignedIn(auth.currentUser?.email, error.localizedMessage)
                return@addSnapshotListener
            }
            val remote = snapshot?.getString(FIELD_DATA)?.let { Repository.decode(it) }
            if (remote == null) {
                pendingFirstPush?.invoke()
            } else {
                onRemote(remote)
            }
        }
    }

    /** Called when the signed-in user has no cloud copy yet, so the local data gets uploaded. */
    var pendingFirstPush: (() -> Unit)? = null

    fun push(data: AppData) {
        val uid = if (available) auth.currentUser?.uid else null
        uid ?: return
        db.collection(USERS).document(uid).set(
            mapOf(FIELD_DATA to Repository.encode(data), FIELD_MODIFIED to data.modifiedAt),
            SetOptions.merge(),
        )
    }

    /** Google sign-in through Credential Manager; [activityContext] must be an Activity. */
    suspend fun signIn(activityContext: Context): String? {
        if (!available) return "Synchronizacja nie jest skonfigurowana w tej wersji"
        val clientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (clientIdRes == 0) return "Brak identyfikatora klienta Google w konfiguracji Firebase"
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(clientIdRes))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Logowanie nie powiodło się"
        }
    }

    suspend fun signOut() {
        if (!available) return
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private companion object {
        const val USERS = "users"
        const val FIELD_DATA = "data"
        const val FIELD_MODIFIED = "modifiedAt"
    }
}
