package com.moleculesoft.dcs.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.moleculesoft.dcs.DcsApplication
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val supabase = DcsApplication.supabase

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                _authState.value = AuthState.Authenticated
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        val activity = context.findActivity()
        if (activity == null) {
            _authState.value = AuthState.Error("Activity context not found")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credentialManager = CredentialManager.create(activity)
                
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    // Ensure this matches your Web Client ID from Google Cloud Console
                    .setServerClientId("591798744626-2obtaj77pb7ihfguq7ek0bb70rht1qrl.apps.googleusercontent.com")
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                Log.d("AuthViewModel", "Launching Credential Manager...")
                val result = credentialManager.getCredential(activity, request)
                handleSignIn(result, hashedNonce)
            } catch (e: GetCredentialException) {
                Log.e("AuthViewModel", "Credential Manager Error: ${e.type}", e)
                _authState.value = AuthState.Error("Sign-in canceled or failed: ${e.message}")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Unexpected Error", e)
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private suspend fun handleSignIn(result: GetCredentialResponse, nonce: String) {
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val idToken = credential.idToken
            try {
                supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    this.provider = Google
                    this.nonce = nonce
                }
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                e.printStackTrace()
                _authState.value = AuthState.Error(e.message ?: "Supabase Sign-in failed")
            }
        } else {
            _authState.value = AuthState.Error("Unexpected credential type")
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Stop the sensor service
            val intent = android.content.Intent(context, com.moleculesoft.dcs.service.SensorService::class.java)
            context.stopService(intent)

            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _authState.value = AuthState.Unauthenticated
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
