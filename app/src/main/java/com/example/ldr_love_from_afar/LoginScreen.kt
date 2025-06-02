package com.example.ldr_love_from_afar

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ldr_love_from_afar.R
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onRegisterClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val auth = FirebaseAuth.getInstance()
    val oneTapClient = remember { Identity.getSignInClient(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(activity) { task ->
                            if (task.isSuccessful) {
                                Log.d("Login", "Google sign-in success")
                                onLoginSuccess()
                            } else {
                                Log.w("Login", "signInWithCredential:failure", task.exception)
                                errorMessage = task.exception?.message
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("Login", "Google sign-in failed", e)
                errorMessage = e.message
            }
        }
    }

    fun launchGoogleSignIn() {
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId("YOUR-WEB-CLIENT-ID.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                launcher.launch(
                    IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                Log.e("Login", "Sign-in failed", e)
                errorMessage = e.message
            }
    }

    fun signInWithEmail() {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    Log.d("Login", "Email sign-in success")
                    onLoginSuccess()
                } else {
                    Log.w("Login", "signInWithEmail:failure", task.exception)
                    errorMessage = task.exception?.message
                }
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White // swapped to white background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 24.dp),
                contentScale = ContentScale.Fit
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = Color.Black) }, // label now black
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(color = Color.Black), // text now black
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.Black) }, // label now black
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(color = Color.Black), // text now black
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { signInWithEmail() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black), // button background black
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign in with Email", color = Color.White, fontWeight = FontWeight.Bold) // text becomes white
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { launchGoogleSignIn() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black), // button background black
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold) // text becomes white
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onRegisterClick) {
                Text("Don't have an account? Register", color = Color.Black, fontSize = 14.sp) // link text now black
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = Color.Red, fontSize = 12.sp)
            }
        }
    }
}
