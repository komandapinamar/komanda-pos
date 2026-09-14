package com.komanda.business.core.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komanda.business.ui.theme.Amber400
import com.komanda.business.ui.theme.Red400
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (email: String, password: String) -> Unit,
    serverUrl: String = "",
    onServerUrlChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var showServerDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Zinc950),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Komanda Business",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Thin,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "Terminal Punto de Venta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc400,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x33F87171)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = Red400,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Zinc800,
                        focusedLabelColor = Amber400,
                        unfocusedLabelColor = Zinc400,
                        cursorColor = Amber400
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                onLogin(email, password)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Zinc800,
                        focusedLabelColor = Amber400,
                        unfocusedLabelColor = Zinc400,
                        cursorColor = Amber400
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onLogin(email, password)
                    },
                    enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber400,
                        contentColor = Zinc950,
                        disabledContainerColor = Zinc800,
                        disabledContentColor = Zinc400
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Zinc950,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Iniciar Sesión",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }

                if (onServerUrlChanged != null && serverUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            tempServerUrl = serverUrl
                            showServerDialog = true
                        }
                    ) {
                        Text(
                            text = "Servidor: $serverUrl",
                            color = Zinc400,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (showServerDialog) {
                        AlertDialog(
                            onDismissRequest = { showServerDialog = false },
                            containerColor = Zinc900,
                            title = {
                                Text(
                                    text = "Servidor backend",
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "URL del servidor Komanda (ej: para Telpo físico en Wi-Fi o emulador):",
                                        color = Zinc400,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedTextField(
                                        value = tempServerUrl,
                                        onValueChange = { tempServerUrl = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Amber400,
                                            unfocusedBorderColor = Zinc800,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Amber400
                                        )
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { tempServerUrl = "https://throwing-dust-public.ngrok-free.dev" }) {
                                            Text("127.0.0.1 (USB)", fontSize = 11.sp, color = Amber400)
                                        }
                                        TextButton(onClick = { tempServerUrl = "http://10.0.2.2:3000" }) {
                                            Text("10.0.2.2 (AVD)", fontSize = 11.sp, color = Amber400)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showServerDialog = false
                                        val trimmed = tempServerUrl.trim().trimEnd('/')
                                        if (trimmed.isNotBlank()) {
                                            onServerUrlChanged(trimmed)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Amber400,
                                        contentColor = Zinc950
                                    )
                                ) {
                                    Text("Guardar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showServerDialog = false }) {
                                    Text("Cancelar", color = Zinc400)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
