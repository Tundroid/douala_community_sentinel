package com.moleculesoft.dcs.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moleculesoft.dcs.DcsApplication
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val supabase = DcsApplication.supabase

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    _authState.value = AuthState.Authenticated
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Check session error", e)
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password.trim()
                }
                _authState.value = AuthState.Authenticated
            } catch (e: AuthRestException) {
                Log.e("AuthViewModel", "Sign in error: ${e.error} - ${e.description}", e)
                val message = when (e.error) {
                    "invalid_credentials" -> "Invalid email or password"
                    "user_not_found" -> "User not found"
                    else -> e.description ?: "Login failed"
                }
                _authState.value = AuthState.Error(message)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign in error", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password.trim()
                }
                // Supabase usually logs in after signup, but depends on confirmation settings
                // If email confirmation is required, session might be null.
                checkSession()
            } catch (e: AuthRestException) {
                Log.e("AuthViewModel", "Sign up error: ${e.error} - ${e.description}", e)
                _authState.value = AuthState.Error(e.description ?: "Sign up failed")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign up error", e)
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun resetError() {
        _authState.value = AuthState.Unauthenticated
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
