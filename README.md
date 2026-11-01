# Notif Bridge

Espelha as notificações de um celular Android (Samsung Galaxy A35) no desktop Arch Linux.

```
[Android: NotificationListenerService] --HTTP POST JSON--> [Arch: servidor pequeno] --> notify-send
```

## Como funciona

O fluxo tem três etapas. Cada uma é um componente independente.

### 1. Android: `NotificationListenerService`

`NotificationListenerService` é uma API do Android que permite a um app receber uma cópia de **todas** as notificações do sistema, de qualquer app. Não é preciso interceptar nada: o sistema chama o método `onNotificationPosted()` toda vez que uma notificação aparece.

Para isso funcionar:

- O serviço é declarado no `AndroidManifest.xml` com a permissão `BIND_NOTIFICATION_LISTENER_SERVICE`.
- O usuário concede o **Acesso a notificações** manualmente em *Configurações*. Não existe diálogo de permissão em tempo de execução para isso.
- O serviço roda em segundo plano, gerenciado pelo próprio sistema.

Em `onNotificationPosted(sbn)`, o app lê do objeto `StatusBarNotification`:

| Campo | Origem |
|-------|--------|
| `app` | nome do app que gerou a notificação |
| `title` | `Notification.EXTRA_TITLE` |
| `text` | `Notification.EXTRA_TEXT` |

Notificações persistentes (`isOngoing`, como player de música ou VPN ativa) e as do próprio app são ignoradas, para evitar ruído e laços de repetição.

### 2. Transporte: HTTP POST com JSON

O app envia cada notificação como uma requisição HTTP para o Arch:

```http
POST /notify HTTP/1.1
Host: 192.168.0.50:8787
Authorization: Bearer <token>
Content-Type: application/json

{"app": "WhatsApp", "title": "Maria", "text": "Chego em 10 min"}
```

Por que HTTP + JSON:

- Simples de implementar dos dois lados (OkHttp no Android, biblioteca padrão no Python).
- Fácil de depurar com `curl`.
- O header `Authorization: Bearer` impede que qualquer dispositivo na rede dispare notificações no seu desktop.

O envio é assíncrono (`enqueue`), para não bloquear o serviço de notificações.

### 3. Arch Linux: servidor pequeno + `notify-send`

Um script Python (`receiver.py`) usa só a biblioteca padrão (`http.server`). Ele:

1. Escuta na porta `8787`.
2. Valida o caminho (`/notify`) e o token. Se algum estiver errado, responde `403`.
3. Lê o JSON do corpo da requisição.
4. Chama `notify-send`, que entrega a mensagem ao **daemon de notificações** do desktop (dunst, mako, KDE, GNOME etc.) via D-Bus.
5. Responde `204 No Content`.

`notify-send` faz parte do pacote `libnotify`. Ele só exibe a notificação: quem a desenha na tela é o daemon de notificações da sua sessão. Sem um daemon rodando, nada aparece.

## Requisitos

**Android**
- Android Studio, Kotlin, minSdk 26
- Celular com Depuração USB (ou sem fio) ativada

**Arch Linux**
```bash
sudo pacman -S libnotify python
```
Mais um daemon de notificações ativo na sessão.

## Configuração

### Android

O projeto já está configurado (OkHttp, manifest, serviço e tela de configuração). Basta:

1. Abrir a pasta no Android Studio e rodar no celular.
2. No app, tocar em **Abrir acesso a notificações** e ativar *Notif Bridge*.
3. Preencher **Servidor** (ex.: `192.168.0.50:8787`) e **Token**. Ficam salvos no aparelho.
4. Tocar em **Enviar notificação de teste** depois de subir o receptor no Arch.

### Arch

1. Copie `arch/receiver.py` para o Arch e gere um token:
   ```bash
   openssl rand -hex 32
   ```
2. Rode, passando o token por variável de ambiente (o mesmo valor do app):
   ```bash
   NOTIF_BRIDGE_TOKEN=SEU_TOKEN python receiver.py
   ```
   Opcionais: `NOTIF_BRIDGE_HOST` (padrão `0.0.0.0`) e `NOTIF_BRIDGE_PORT` (padrão `8787`).
3. Libere a porta no firewall, restringindo à sua rede local:
   ```bash
   sudo ufw allow from 192.168.0.0/24 to any port 8787
   ```

### Testar sem o celular

```bash
curl -X POST http://localhost:8787/notify   -H "Authorization: Bearer SEU_TOKEN"   -H "Content-Type: application/json"   -d '{"app":"Teste","title":"Olá","text":"Funcionou"}'
```

Se uma notificação aparecer no desktop, o lado Arch está pronto.

### Rodar como serviço (systemd de usuário)

```bash
mkdir -p ~/.local/share/notif-bridge ~/.config/systemd/user
cp arch/receiver.py ~/.local/share/notif-bridge/
cp arch/notif-bridge.service ~/.config/systemd/user/
echo "NOTIF_BRIDGE_TOKEN=SEU_TOKEN" > ~/.config/notif-bridge.env
chmod 600 ~/.config/notif-bridge.env
systemctl --user enable --now notif-bridge.service
```

Use um serviço de **usuário**, não de sistema. O `notify-send` precisa do barramento D-Bus da sessão gráfica.

## Samsung One UI: pontos de atenção

- **Bateria:** defina o app como *Sem restrições* e remova-o das listas de apps em suspensão em *Cuidados com o dispositivo*. Caso contrário o sistema encerra o serviço.
- **Configurações restritas (Android 13+):** para APK instalado fora da Play Store, use *Informações do app → ⋮ → Permitir configurações restritas* se o acesso a notificações estiver bloqueado.
- **Nome dos apps:** a partir do Android 11, ler o nome de outros apps exige `<queries>` ou `QUERY_ALL_PACKAGES`. Sem isso, o código usa o nome do pacote como fallback.

## Segurança

- O exemplo usa HTTP em texto puro (`usesCleartextTraffic="true"`) para facilitar testes em LAN. O conteúdo das notificações (mensagens, códigos de verificação) trafega sem criptografia.
- Para uso real, prefira uma rede privada (Tailscale ou WireGuard) ou HTTPS, e remova o `usesCleartextTraffic`.
- Use um token longo e aleatório (`openssl rand -hex 32`). Ele fica só no app e em variável de ambiente, nunca no repositório.
- Restrinja o firewall à sua rede ou à interface da VPN.

## Estrutura

```
.
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/com/example/myapplication/
│       ├── MainActivity.kt     # tela: acesso a notificações, servidor, token, teste
│       ├── NotifyListener.kt   # NotificationListenerService
│       ├── Sender.kt           # HTTP POST com OkHttp
│       └── BridgeConfig.kt     # servidor e token (SharedPreferences)
├── arch/
│   ├── receiver.py
│   └── notif-bridge.service
└── README.md
```

## Próximos passos

- Filtro por app (lista de permitidos/bloqueados).
- Fila com retry (WorkManager) quando o PC estiver offline.
- Descoberta automática do servidor via mDNS/Avahi.
- Sincronizar o descarte: ao dispensar no celular, fechar a notificação no desktop.
