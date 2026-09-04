package com.komanda.business.core.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komanda.business.ui.theme.Red400
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950

@Composable
fun NoActiveTenantScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Acceso No Disponible",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Red400
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "No tenés ningún restaurante activo asignado a tu cuenta. Contactá al administrador para obtener acceso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc400,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Zinc800,
                        contentColor = Zinc400
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Volver al Inicio de Sesión", fontSize = 14.sp)
                }
            }
        }
    }
}
