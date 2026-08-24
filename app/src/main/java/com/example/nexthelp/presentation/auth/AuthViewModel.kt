package com.example.nexthelp.presentation.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<User>?>(null)
    val loginState: StateFlow<Resource<User>?> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<Resource<User>?>(null)
    val registerState: StateFlow<Resource<User>?> = _registerState.asStateFlow()

    private val _resetPasswordState = MutableStateFlow<Resource<Unit>?>(null)
    val resetPasswordState: StateFlow<Resource<Unit>?> = _resetPasswordState.asStateFlow()

    val currentUser get() = repository.currentUser

    private val _eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _loginState.value = Resource.Loading()
            when (val result = repository.loginWithEmail(email, pass)) {
                is Resource.Success -> {
                    _loginState.value = result
                    _eventFlow.emit(UiEvent.LoginSuccess(result.data!!))
                }
                is Resource.Error -> {
                    _loginState.value = result
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Login failed"))
                }
                else -> Unit
            }
        }
    }

    fun register(email: String, fullName: String, pass: String) {
        viewModelScope.launch {
            _registerState.value = Resource.Loading()
            when (val result = repository.registerWithEmail(email, fullName, pass)) {
                is Resource.Success -> {
                    _registerState.value = result
                    _eventFlow.emit(UiEvent.RegisterSuccess)
                }
                is Resource.Error -> {
                    _registerState.value = result
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Registration failed"))
                }
                else -> Unit
            }
        }
    }

    fun loginWithGoogle(context: Context) {
        // Requires google-services.json + a configured web client ID.
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowSnackbar("Google Sign-In is not configured yet."))
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Please enter your email"))
                return@launch
            }
            _resetPasswordState.value = Resource.Loading()
            when (val result = repository.sendPasswordResetEmail(email)) {
                is Resource.Success -> {
                    _resetPasswordState.value = result
                    _eventFlow.emit(UiEvent.ShowSnackbar("Reset email sent! Check your inbox."))
                }
                is Resource.Error -> {
                    _resetPasswordState.value = result
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to send reset email"))
                }
                else -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun updateDisplayName(fullName: String) {
        viewModelScope.launch {
            repository.updateDisplayName(fullName)
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        data class LoginSuccess(val user: User) : UiEvent()
        object RegisterSuccess : UiEvent()
    }
}
