package com.example.myapplication

import android.os.Build
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Envia uma notificacao ao receptor via HTTP POST com JSON. */
object Sender {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** [onResult] recebe `null` em caso de sucesso, ou a mensagem de erro. Roda em thread de rede. */
    fun send(
        config: BridgeConfig,
        app: String,
        title: String,
        text: String,
        onResult: ((String?) -> Unit)? = null,
    ) {
        val request = try {
            val body = JSONObject()
                .put("app", app)
                .put("title", title)
                .put("text", text)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .toString()
                .toRequestBody(json)
            Request.Builder()
                .url(config.endpoint())
                .header("Authorization", "Bearer ${config.token}")
                .post(body)
                .build()
        } catch (e: IllegalArgumentException) {
            onResult?.invoke("Endereco do servidor invalido")
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult?.invoke(e.message ?: "Falha de conexao")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult?.invoke(if (it.isSuccessful) null else "Servidor respondeu HTTP ${it.code}")
                }
            }
        })
    }
}
