package org.demonsaw.app

import android.graphics.Color.parseColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

private val DsBlue = Color(0xFF0C9BDC)
private val DsBackground = Color(0xFF0F1419)
private val DsSurface = Color(0xFF1A2129)
private val DsSurfaceHigh = Color(0xFF232C36)

private val DemonsawColors = darkColorScheme(
    primary = DsBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF52C175),
    background = DsBackground,
    onBackground = Color(0xFFE6EDF3),
    surface = DsSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = DsSurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB0C0),
    error = Color(0xFFE5534B),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = DemonsawColors) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val context = this

                when {
                    state.connecting -> ConnectingScreen()
                    state.running -> MainScreen(
                        state = state,
                        onSend = vm::send,
                        onBrowse = vm::browse,
                        onJoin = vm::join,
                        onDisconnect = vm::disconnect,
                    )
                    else -> ConnectScreen(
                        error = state.error,
                        onConnect = { host, port, name, passphrase ->
                            vm.connect(context, host, port, name, passphrase)
                        },
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun ConnectingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DsBlue)
            Spacer(Modifier.height(16.dp))
            Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectScreen(
    error: String?,
    onConnect: (String, Int, String, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("demonsaw", 0) }

    var host by remember { mutableStateOf(prefs.getString("host", "router.demonsaw.com") ?: "router.demonsaw.com") }
    var port by remember { mutableStateOf((prefs.getInt("port", 80)).toString()) }
    var name by remember { mutableStateOf(prefs.getString("name", "Android") ?: "Android") }
    var passphrase by remember { mutableStateOf(prefs.getString("passphrase", "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "demonsaw",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFE6EDF3),
        )
        Text(
            text = "Believe in the Right to Share",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 16) name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Router address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Group passphrase") },
            supportingText = { Text("Everyone who enters the same passphrase joins your community.") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val parsedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 1337
                onConnect(host.trim(), parsedPort, name.trim(), passphrase)
            },
            enabled = host.isNotBlank() && port.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text("Start", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Demonsaw 4 for Android — free and open source, MIT licensed.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onBrowse: () -> Unit,
    onJoin: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.snapshot.name.ifBlank { "Demonsaw" }, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(state.snapshot.status)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = state.snapshot.groupLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBrowse) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Browse clients")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DsSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DsSurface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.People, contentDescription = null) },
                    label = { Text("People") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                0 -> ChatPane(state.snapshot, onSend)
                1 -> PeoplePane(state.snapshot, onBrowse)
                else -> SettingsPane(state.snapshot, onJoin, onDisconnect)
            }
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    val color = when (status) {
        "success" -> Color(0xFF52C175)
        "pending", "info" -> Color(0xFFD9A441)
        "warning" -> Color(0xFFD97C41)
        "error" -> Color(0xFFE5534B)
        else -> Color(0xFF6B7B8C)
    }

    Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun ChatPane(snapshot: Snapshot, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(snapshot.chat.size) {
        if (snapshot.chat.isNotEmpty()) {
            listState.animateScrollToItem(snapshot.chat.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (snapshot.chat.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No messages yet.\nSay hello to the group.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(snapshot.chat) { message ->
                    MessageBubble(message, mine = message.name == snapshot.name)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(DsBlue, CircleShape),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean) {
    val accent = remember(message.color) {
        try {
            Color(parseColor("#${message.color.ifBlank { "0c9bdc" }}"))
        } catch (t: Throwable) {
            DsBlue
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine) {
            Text(
                text = message.name.ifBlank { "unknown" },
                fontSize = 11.sp,
                color = accent,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (mine) DsBlue.copy(alpha = 0.85f) else DsSurfaceHigh,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(message.text, color = Color(0xFFE6EDF3))
            if (message.time > 0) {
                Text(
                    text = remember(message.time) {
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.time * 1000))
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF9FB0C0),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun PeoplePane(snapshot: Snapshot, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${snapshot.clients.size} client(s) online", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBrowse) { Text("Browse") }
        }

        if (snapshot.clients.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No one else is here yet.\nShare the router and passphrase with your friends.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshot.clients) { peer ->
                    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DsSurface)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val color = try {
                                Color(parseColor("#${peer.color.ifBlank { "0c9bdc" }}"))
                            } catch (t: Throwable) {
                                DsBlue
                            }
                            Box(Modifier.size(10.dp).background(color, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(peer.name.ifBlank { "unknown" }, modifier = Modifier.weight(1f))
                            StatusDot(peer.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPane(snapshot: Snapshot, onJoin: () -> Unit, onDisconnect: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Community", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Text(snapshot.groupLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Share this group with your friends. They join by using the same router and passphrase.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text("Client", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Text(snapshot.name.ifBlank { "—" })
        Text("Status: ${snapshot.status}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))

        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
            Text("Join group")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}
