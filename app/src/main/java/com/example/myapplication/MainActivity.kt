package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var accessGranted by mutableStateOf(false)
    private var status by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val config = BridgeConfig(this)
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        config = config,
                        accessGranted = accessGranted,
                        status = status,
                        onOpenAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onTest = { sendTest(config) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // O usuario concede o acesso em outra tela; reavalia ao voltar.
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun sendTest(config: BridgeConfig) {
        status = "Enviando..."
        Sender.send(config, "Notif Bridge", "Teste", "Se voce viu isto no desktop, esta funcionando.") { error ->
            runOnUiThread { status = error?.let { "Erro: $it" } ?: "Enviado com sucesso" }
        }
    }
}

@Composable
private fun SettingsScreen(
    config: BridgeConfig,
    accessGranted: Boolean,
    status: String,
    onOpenAccess: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var server by remember { mutableStateOf(config.server) }
    var token by remember { mutableStateOf(config.token) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Notif Bridge", style = MaterialTheme.typography.headlineMedium)

        Text(
            if (accessGranted) "Acesso a notificacoes: concedido"
            else "Acesso a notificacoes: NAO concedido",
        )
        OutlinedButton(onClick = onOpenAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir acesso a notificacoes")
        }

        OutlinedTextField(
            value = server,
            onValueChange = { server = it; config.server = it },
            label = { Text("Servidor (ex.: 192.168.0.50:8787)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; config.token = it },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onTest,
            enabled = server.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enviar notificacao de teste")
        }
        if (status.isNotEmpty()) Text(status)
    }
}
