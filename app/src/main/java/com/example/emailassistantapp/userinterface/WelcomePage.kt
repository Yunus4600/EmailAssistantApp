package com.example.emailassistantapp.userinterface

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emailassistantapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emailassistantapp.viewmodel.EmailViewModel
import android.content.Intent
import android.net.Uri
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import com.example.emailassistantapp.data.model.Email
import android.widget.Toast
import androidx.navigation.NavController
import com.example.emailassistantapp.components.CustomToast
import com.example.emailassistantapp.components.ToastType
import com.example.emailassistantapp.components.rememberToastState
import com.example.emailassistantapp.components.showErrorToast
import com.example.emailassistantapp.components.showSuccessToast
import com.example.emailassistantapp.components.showToast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

data class Message(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomePage(
    navController: NavController,
    userEmail: String?,
    onLogout: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var showWelcomeCard by remember { mutableStateOf(true) }
    var userName by remember { mutableStateOf("") }
    var showEmailPasswordDialog by remember { mutableStateOf(false) }
    var emailPassword by remember { mutableStateOf("") }
    var emailPasswordError by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var selectedEmailIndex by remember { mutableStateOf<Int?>(null) }
    var aiResponses by remember { mutableStateOf<List<String>>(emptyList()) }
    var userEmail by remember { mutableStateOf("") } // Change from val to var

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val buttonColor = Color(0xFF007AFF)
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val emailViewModel: EmailViewModel = viewModel()
    val context = LocalContext.current

    val isLoading by emailViewModel.isLoading.collectAsState()
    val error by emailViewModel.error.collectAsState()
    val emails by emailViewModel.emails.collectAsState()

    val toastState = rememberToastState()

    LaunchedEffect(auth.currentUser) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmailPasswordDialog = false
            emailPassword = ""
            emailPasswordError = null
            messages = emptyList()
            showWelcomeCard = true
            retryCount = 0
        } else {
            db.collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    userName = document.getString("fullName") ?: "User"
                    userEmail = document.getString("email") ?: currentUser.email ?: "" // Provide default empty string
                    showEmailPasswordDialog = true
                }
                .addOnFailureListener { e ->
                    messages = listOf(Message("Error fetching user data: ${e.message}", false))
                }
        }
    }

    if (showEmailPasswordDialog) {
        AlertDialog(
            onDismissRequest = { /* Dialog cannot be dismissed */ },
            title = { Text("Email Password Required") },
            text = {
                Column {
                    Text("Please enter your email password to connect to your email account.")
                    if (retryCount > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "Previous attempt failed. Please check:",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (emailPasswordError != null) {
                                emailPasswordError?.split("\n")?.forEach { line ->
                                    Text(
                                        "• $line",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailPassword,
                        onValueChange = {
                            emailPassword = it
                            emailPasswordError = null
                        },
                        label = { Text("Email Password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = emailPasswordError != null,
                        supportingText = emailPasswordError?.let { { Text(it) } },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = buttonColor,
                            focusedLabelColor = buttonColor,
                            errorBorderColor = Color.Red,
                            errorLabelColor = Color.Red
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: If you're using Gmail with 2FA, you need to use an App Password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "App Password format: Enter the 16-character password exactly as shown (no spaces)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/security"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Open Gmail Settings")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val sharedPref = context.getSharedPreferences("email_prefs", Context.MODE_PRIVATE)
                            emailPassword = sharedPref.getString("saved_email_password", "") ?: ""
                        },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Load Saved Password")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (emailPassword.isEmpty()) {
                            emailPasswordError = "Password cannot be empty"
                        } else {
                            scope.launch {
                                try {
                                    emailViewModel.connect(userEmail, emailPassword)
                                    val sharedPref = context.getSharedPreferences("email_prefs", Context.MODE_PRIVATE)
                                    with(sharedPref.edit()) {
                                        putString("saved_email_password", emailPassword)
                                        apply()
                                    }
                                    showEmailPasswordDialog = false
                                    retryCount = 0
                                    toastState.showSuccessToast("Successfully connected to email")
                                } catch (e: Exception) {
                                    emailPasswordError = e.message
                                    retryCount++
                                    toastState.showErrorToast("Failed to connect: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Connect")
                }
            }
        )
    }

    if (!showEmailPasswordDialog && retryCount > 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    showEmailPasswordDialog = true
                    emailPassword = ""
                    emailPasswordError = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    Text("Retry Email Connection")
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            messages = listOf(Message("Error: $error", false))
        }
    }

    LaunchedEffect(emails) {
        if (emails.isNotEmpty()) {
            toastState.showSuccessToast("Successfully fetched ${emails.size} emails")
        }
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.email_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(32.dp)
                            )
                            Text("Email Assistant")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isOpen) {
                                        drawerState.close()
                                    } else {
                                        drawerState.open()
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(buttonColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(56.dp)) // Add top spacer to match TopAppBar height

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(buttonColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = userName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = userEmail ?: "", // Provide default empty string
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor
                            )
                        ) {
                            Text("Logout")
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp) // Add bottom padding for the textbox
                        .verticalScroll(rememberScrollState())
                ) {
                    if (error != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Red.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Connection Error",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                error?.split("\n")?.forEach { line ->
                                    Text(
                                        "• $line",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        showEmailPasswordDialog = true
                                        emailPassword = ""
                                        emailPasswordError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor
                                    )
                                ) {
                                    Text("Retry Connection")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showWelcomeCard,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        WelcomeCard(
                            userName = userName,
                            onFetchReport = {
                                showWelcomeCard = false
                                scope.launch {
                                    try {
                                        emailViewModel.loadEmails()
                                    } catch (e: Exception) {
                                        messages = listOf(Message("Error: ${e.message}", false))
                                    }
                                }
                            }
                        )
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = buttonColor)
                        }
                    }

                    // Email summary cards (option selection)
                    emails.withIndex().forEach { (index, email) ->
                        EmailSummaryCard(email = email, index = index)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Chat-like UI for selected summary and AI responses
                    if (selectedEmailIndex != null) {
                        val selectedEmail = emails[selectedEmailIndex!!]
                        MessageBubble(
                            message = Message(
                                content = selectedEmail.summary ?: "Processing summary...",
                                isUser = false
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        aiResponses.forEach { response ->
                            val context = LocalContext.current
                            Surface(
                                modifier = Modifier
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface // Use theme color
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = response,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("AI Response", response)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    }
                                }
                            }
                        }
                    }
                }
                // Static textbox at the bottom with proper padding and background
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            placeholder = { Text("Enter option number (1-${emails.size})...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = buttonColor,
                                focusedLabelColor = buttonColor
                            ),
                            enabled = !isLoading,
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    try {
                                        val optionNumber = messageText.toInt()
                                        if (optionNumber in 1..emails.size) {
                                            selectedEmailIndex = optionNumber - 1
                                            val selectedEmail = emails[selectedEmailIndex!!]
                                            scope.launch {
                                                try {
                                                    messages = messages + Message(
                                                        "Generating AI responses for Email Option $optionNumber...",
                                                        false
                                                    )
                                                    emailViewModel.generateResponses(selectedEmail.content) { responses ->
                                                        aiResponses = responses
                                                        messages = messages + Message(
                                                            "Suggested Responses for Email ${optionNumber}:",
                                                            false
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    messages = messages + Message("Error: ${e.message}", false)
                                                }
                                            }
                                        } else {
                                            messages = messages + Message(
                                                "Please enter a valid option number between 1 and ${emails.size}",
                                                false
                                            )
                                        }
                                    } catch (e: NumberFormatException) {
                                        messages = messages + Message(
                                            "Please enter a valid number",
                                            false
                                        )
                                    }
                                    messageText = ""
                                }
                            },
                            enabled = !isLoading && messageText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = buttonColor)
                    }
                }
            }
        }

        CustomToast(
            toastData = toastState.value,
            onDismiss = { toastState.value = null }
        )
    }
}

@Composable
fun EmailItem(email: Email) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = email.from,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(email.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email.subject,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (email.hasAttachment) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Has Attachment",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Attachment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmailSummaryCard(
    email: Email,
    index: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Option ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(email.date),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "From: ${email.from}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Subject: ${email.subject}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F7FF)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "AI Summary:",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF007AFF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email.summary ?: "Processing summary...", // Already has default value
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Would you like to reply with an AI-suggested response? Enter Option ${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF007AFF),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.smarttoy_logo),
                        contentDescription = "AI",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = if (message.isUser) Color(0xFF007AFF) else Color(0xFFF5F5F5)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = if (message.isUser) Color.White else Color.Black
                )
            }
        }
    }
}

@Composable
fun WelcomeCard(
    userName: String,
    onFetchReport: () -> Unit
) {
    val buttonColor = Color(0xFF007AFF)
    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val animatedColor by infiniteTransition.animateColor(
        initialValue = buttonColor,
        targetValue = Color(0xFF00C853),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(buttonColor, animatedColor)
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Welcome, $userName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = buttonColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your AI-Powered Email Assistant is ready to help you manage your emails efficiently.",
                fontSize = 16.sp,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "How to use:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = buttonColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "1. Click 'Fetch Daily Report' to get today's email summary\n" +
                        "2. Type the option number of the email you want to reply to\n" +
                        "3. Select the AI-suggested response\n" +
                        "4. Confirm to send the AI-generated reply",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFetchReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Fetch",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Fetch Daily Report")
                }
            }
        }
    }
}