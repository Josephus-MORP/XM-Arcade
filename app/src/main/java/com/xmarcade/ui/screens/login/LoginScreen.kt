package com.xmarcade.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xmarcade.R
import com.xmarcade.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    navController: NavController,
    vm: LoginViewModel = hiltViewModel()
) {
    var nsecInput by remember { mutableStateOf("") }
    var showNsecDialog by remember { mutableStateOf(false) }
    var generatedInfo by remember { mutableStateOf<Pair<String,String>?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(XmNavy)) {
        // subtle grid background like logo
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo – use app_logo drawable
            // If not found fall back to text
            Box(
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)).background(XmSurface),
                contentAlignment = Alignment.Center
            ) {
                // coil will load from drawable if we pass? simpler: text logo
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("M", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MoneroOrange)
                    }
                    Text("ARCADE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 4.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Welcome to", fontSize = 14.sp, color = TextMuted, letterSpacing = 2.sp)
            Text("XM Arcade", fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("Nostr. Fun. Not just micro-blogging.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top=6.dp))

            Spacer(Modifier.height(32.dp))

            // Primary button: Create Account
            Button(
                onClick = {
                    scope.launch {
                        val pair = vm.createNewAccount()
                        generatedInfo = pair
                        onLoginSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            // Secondary: Sign with nsec
            OutlinedButton(
                onClick = { showNsecDialog = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DividerDark)
            ) {
                Text("Sign in with nsec", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            // Tertiary: Login with Amber
            Button(
                onClick = { vm.loginWithAmber(onLoginSuccess) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XmCard, contentColor = TextPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Login with Amber", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = XmCyan)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "New accounts get default relays: nos.lol, ditto.pub, primal, fountain.fm\nBlossom: ditto.pub, primal.net, data.haus\nDefault follows #music #fps #nature … editable in Settings",
                fontSize = 11.sp, color = TextMuted, lineHeight = 14.sp
            )
        }
    }

    if (showNsecDialog) {
        AlertDialog(
            onDismissRequest = { showNsecDialog = false },
            title = { Text("Paste your nsec") },
            text = {
                Column {
                    Text("Your nsec never leaves the device. Stored encrypted.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nsecInput,
                        onValueChange = { nsecInput = it },
                        placeholder = { Text("nsec1...") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (vm.loginWithNsec(nsecInput)) {
                                showNsecDialog = false
                                onLoginSuccess()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
                ) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { showNsecDialog = false }) { Text("Cancel") } },
            containerColor = XmSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    generatedInfo?.let { (npub, nsec) ->
        AlertDialog(
            onDismissRequest = { generatedInfo = null },
            title = { Text("Account created!", color = MoneroOrange) },
            text = {
                Column {
                    Text("Save your nsec securely. You can back it up anytime in Settings → Backup nsec.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text("npub:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
                    Text(npub, fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("nsec:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
                    Text(nsec, fontSize = 12.sp, color = MoneroOrange)
                }
            },
            confirmButton = {
                Button(onClick = { generatedInfo = null }, colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)) { Text("Got it") }
            },
            containerColor = XmSurface
        )
    }
}
