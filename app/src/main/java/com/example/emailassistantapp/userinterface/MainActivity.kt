package com.example.emailassistantapp.userinterface

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.emailassistantapp.R
import com.google.firebase.auth.FirebaseAuth
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object Welcome : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("welcome") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onRegisterClick = {
                                    navController.navigate("register")
                                }
                            )
                        }
                        
                        composable("register") {
                            RegisterPage(navController = navController)
                        }
                        
                        composable("welcome") {
                            val auth = FirebaseAuth.getInstance()
                            WelcomePage(
                                navController = navController,
                                userEmail = auth.currentUser?.email,
                                onLogout = {
                                    auth.signOut()
                                    navController.navigate("login") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        composable("email_list") {
                            // TODO: Add EmailListScreen
                            Text("Email List")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val buttonColor = Color(0xFF007AFF)
    val auth = FirebaseAuth.getInstance()

    // Define custom font family
    val montserratFont = FontFamily(
        Font(R.font.montserrat_medium)
    )

    // Email validation function
    fun validateEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$"
        return email.matches(emailRegex.toRegex())
    }

    // Network check function
    fun isNetworkAvailable(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.email_logo),
            contentDescription = "Email Logo",
            modifier = Modifier
                .size(160.dp, 120.dp)
                .padding(bottom = 32.dp)
        )

        // App Description with custom font
        Text(
            text = "Your AI-Powered Email Assistant",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = montserratFont,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Email Input
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailError = if (!validateEmail(it) && it.isNotEmpty()) "Invalid email format" else null
            },
            label = { Text("Email") },
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = buttonColor,
                focusedLabelColor = buttonColor,
                errorBorderColor = Color.Red,
                errorLabelColor = Color.Red
            )
        )

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = buttonColor,
                focusedLabelColor = buttonColor
            )
        )

        // Forgot Password Link
        TextButton(
            onClick = { showForgotPasswordDialog = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Forgot Password?",
                color = buttonColor
            )
        }

        // Login Button with MacBook style
        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(context, "Please enter all fields", Toast.LENGTH_SHORT).show()
                } else if (!validateEmail(email)) {
                    Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                } else {
                    isLoading = true
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                            isLoading = false
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = Color.White
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Log In")
            }
        }

        // Register Link
        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "Don't have an account? Register",
                color = buttonColor
            )
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Enter your registered email address to receive a password reset link.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { 
                            recoveryEmail = it
                            emailError = if (!validateEmail(it) && it.isNotEmpty()) "Invalid email format" else null
                        },
                        label = { Text("Email") },
                        isError = emailError != null,
                        supportingText = emailError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = buttonColor,
                            focusedLabelColor = buttonColor,
                            errorBorderColor = Color.Red,
                            errorLabelColor = Color.Red
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (recoveryEmail.isEmpty()) {
                            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                        } else if (!validateEmail(recoveryEmail)) {
                            Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                        } else if (!isNetworkAvailable(context)) {
                            Toast.makeText(context, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show()
                        } else {
                            // Show loading state
                            isLoading = true
                            
                            // Send password reset email
                            auth.sendPasswordResetEmail(recoveryEmail)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            "Password reset link sent to your email. Please check your inbox.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        showForgotPasswordDialog = false
                                    } else {
                                        // Enhanced error handling
                                        val errorMessage = when (task.exception) {
                                            is FirebaseAuthException -> {
                                                when ((task.exception as FirebaseAuthException).errorCode) {
                                                    "ERROR_INVALID_EMAIL" -> "Invalid email format"
                                                    "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                                                    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your connection"
                                                    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later"
                                                    else -> "Reset failed: ${task.exception?.message}"
                                                }
                                            }
                                            else -> "Reset failed: ${task.exception?.message}"
                                        }
                                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                    }
                                    isLoading = false
                                }
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = buttonColor
                        )
                    } else {
                        Text("Send Reset Link")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FetchDailyReportsButton(onClick: () -> Unit) {
    Button(
        onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                onClick() // Ensure this runs on a background thread
            }
        },
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Fetch Daily Reports")
    }
}