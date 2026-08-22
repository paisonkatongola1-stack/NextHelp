package com.example.nexthelp.presentation.auth

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _loginState = mutableStateOf<Resource<User>?>(null)
    val loginState: State<Resource<User>?> = _loginState

    private val _registerState = mutableStateOf<Resource<User>?>(null)
    val registerState: State<Resource<User>?> = _registerState

    private val _resetPasswordState = mutableStateOf<Resource<Unit>?>(null)
    val resetPasswordState: State<Resource<Unit>?> = _resetPasswordState

    val currentUser = repository.currentUser

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _loginState.value = Resource.Loading()
            val result = repository.loginWithEmail(email, pass)
            _loginState.value = result
            if (result is Resource.Success) {
                _eventFlow.emit(UiEvent.LoginSuccess(result.data!!))
            } else if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Login failed"))
            }
        }
    }

    fun register(email: String, fullName: String, pass: String) {
        viewModelScope.launch {
            _registerState.value = Resource.Loading()
            val result = repository.registerWithEmail(email, fullName, pass)
            _registerState.value = result
            if (result is Resource.Success) {
                _eventFlow.emit(UiEvent.RegisterSuccess)
            } else if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Registration failed"))
            }
        }
    }

    fun loginWithGoogle(context: Context) {
        // Placeholder for Google Login
        // In a real app with google-services.json, we'd use Credential Manager here
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowSnackbar("Google Login requires google-services.json setup"))
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Please enter your email"))
                return@launch
            }
            _resetPasswordState.value = Resource.Loading()
            val result = repository.sendPasswordResetEmail(email)
            _resetPasswordState.value = result
            if (result is Resource.Success) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Reset email sent! Check your inbox."))
            } else if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to send reset email"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        data class LoginSuccess(val user: User) : UiEvent()
        object RegisterSuccess : UiEvent()
    }
}
