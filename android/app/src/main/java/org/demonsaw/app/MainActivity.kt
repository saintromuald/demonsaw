package org.demonsaw.app

import android.content.Intent
import android.graphics.Color.parseColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
private val DsGreen = Color(0xFF52C175)
private val DsBackground = Color(0xFF0F1419)
private val DsSurface = Color(0xFF1A2129)
private val DsSurfaceHigh = Color(0xFF232C36)

private val DemonsawColors = darkColorScheme(
    primary = DsBlue,
    onPrimary = Color.White,
    secondary = DsGreen,
    background = DsBackground,
    onBackground = Color(0xFFE6EDF3),
    surface = DsSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = DsSurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB0C0),
    error = Color(0xFFE5534B),
)

private fun parseHex(hex: String): Color = try {
    Color(parseColor("#${hex.ifBlank { "0c9bdc" }}"))
} catch (t: Throwable) {
    DsBlue
}

private fun formatSize(size: Long): String {
    var value = size.toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$size B" else String.format("%.1f %s", value, units[unit])
}

private fun formatTime(time: Long): String = if (time > 0) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time * 1000))
} else ""

private fun formatClock(time: Long): String = if (time > 0) {
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time * 1000))
} else ""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

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
                        state.running -> MainScreen(state = state, vm = vm)
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
    val context = LocalContext.current
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
        Text(text = "demonsaw", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
        Text(text = "Believe in the Right to Share", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            modifier = Modifier.fillMaxWidth().height(50.dp),
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

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

private enum class Overlay { None, Room, Pm, Browse }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: UiState, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var roomId by remember { mutableStateOf("") }
    var pmId by remember { mutableStateOf("") }
    var pmName by remember { mutableStateOf("") }
    var browseStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val snapshot = state.snapshot

    when (overlay) {
        Overlay.Room -> {
            val room = snapshot.rooms.firstOrNull { it.id == roomId }
            RoomScreen(
                room = room,
                snapshot = snapshot,
                onBack = { overlay = Overlay.None },
                onSend = { vm.sendRoom(roomId, it) },
            )
            return
        }
        Overlay.Pm -> {
            val peer = snapshot.clients.firstOrNull { it.id == pmId }
            PmScreen(
                peer = peer,
                onBack = { overlay = Overlay.None },
                onSend = { vm.sendPm(pmId, it) },
            )
            return
        }
        Overlay.Browse -> {
            val current = browseStack.last()
            BrowseScreen(
                key = current.first,
                title = current.second,
                snapshot = snapshot,
                onBack = {
                    browseStack = browseStack.dropLast(1)
                    if (browseStack.isEmpty()) overlay = Overlay.None
                },
                onOpen = { key, name ->
                    browseStack = browseStack + (key to name)
                    vm.browse(key)
                },
                onDownload = vm::download,
                onDownloadAll = vm::downloadAll,
                onBrowse = vm::browse,
            )
            return
        }
        Overlay.None -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(snapshot.name.ifBlank { "Demonsaw" }, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(snapshot.status)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = snapshot.groupLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Chat, null) }, label = { Text("Chat") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.MeetingRoom, null) }, label = { Text("Rooms") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.People, null) }, label = { Text("People") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Filled.SwapVert, null) }, label = { Text("Transfers") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ChatPane(snapshot) { vm.send(it) }
                1 -> RoomsPane(
                    snapshot = snapshot,
                    onOpenRoom = { id ->
                        roomId = id
                        overlay = Overlay.Room
                    },
                    onJoin = vm::joinRoom,
                    onQuit = vm::quitRoom,
                )
                2 -> PeoplePane(
                    snapshot = snapshot,
                    onPm = { id, name ->
                        pmId = id
                        pmName = name
                        overlay = Overlay.Pm
                    },
                    onBrowse = { key, name ->
                        browseStack = listOf(key to name)
                        overlay = Overlay.Browse
                    },
                    onMute = vm::mute,
                    onBrowseRefresh = vm::refreshRooms,
                )
                3 -> TransfersPane(snapshot, vm)
                else -> SettingsPane(snapshot, vm)
            }
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    val color = when (status) {
        "success" -> DsGreen
        "pending", "info" -> Color(0xFFD9A441)
        "warning" -> Color(0xFFD97C41)
        "error" -> Color(0xFFE5534B)
        else -> Color(0xFF6B7B8C)
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

// ---------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------

@Composable
private fun ChatPane(snapshot: Snapshot, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(snapshot.chat.size) {
        if (snapshot.chat.isNotEmpty()) listState.animateScrollToItem(snapshot.chat.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (snapshot.chat.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No messages yet.\nSay hello to the group.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        MessageInput(
            draft = draft,
            onDraft = { draft = it },
            onSend = {
                if (it.isNotBlank()) {
                    onSend(it)
                    draft = ""
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean) {
    val accent = parseHex(message.color)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine) {
            Text(message.name.ifBlank { "unknown" }, fontSize = 11.sp, color = accent, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(if (mine) DsBlue.copy(alpha = 0.85f) else DsSurfaceHigh, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(message.text, color = Color(0xFFE6EDF3))
            if (message.time > 0) {
                Text(formatClock(message.time), fontSize = 10.sp, color = Color(0xFF9FB0C0), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun MessageInput(draft: String, onDraft: (String) -> Unit, onSend: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text("Message") },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(draft) }),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { onSend(draft) },
            modifier = Modifier.size(48.dp).background(DsBlue, CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
// Rooms
// ---------------------------------------------------------------------------

@Composable
private fun RoomsPane(
    snapshot: Snapshot,
    onOpenRoom: (String) -> Unit,
    onJoin: (String) -> Unit,
    onQuit: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (snapshot.rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No rooms. Rooms are defined in the router config.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshot.rooms) { room ->
                    Card(colors = CardDefaults.cardColors(containerColor = DsSurface)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MeetingRoom, null, tint = parseHex(room.color))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(room.name, fontWeight = FontWeight.SemiBold)
                                Text("${room.size} member(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (room.joined) {
                                TextButton(onClick = { onQuit(room.id) }) { Text("Leave") }
                                TextButton(onClick = { onOpenRoom(room.id) }) { Text("Open") }
                            } else {
                                Button(onClick = { onJoin(room.id) }) { Text("Join") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomScreen(room: Room?, snapshot: Snapshot, onBack: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chat = room?.chat ?: emptyList()

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(room?.name ?: "Room", fontWeight = FontWeight.SemiBold)
                        Text("${room?.size ?: 0} member(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DsSurface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (chat.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No messages in this room yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chat) { message ->
                        MessageBubble(message, mine = message.name == snapshot.name)
                    }
                }
            }

            MessageInput(draft = draft, onDraft = { draft = it }, onSend = {
                if (it.isNotBlank()) {
                    onSend(it)
                    draft = ""
                }
            })
        }
    }
}

// ---------------------------------------------------------------------------
// People (PM, mute, browse)
// ---------------------------------------------------------------------------

@Composable
private fun PeoplePane(
    snapshot: Snapshot,
    onPm: (String, String) -> Unit,
    onBrowse: (String, String) -> Unit,
    onMute: (String, Boolean) -> Unit,
    onBrowseRefresh: () -> Unit,
) {
    val peers = snapshot.clients.filter { it.id != snapshot.self }
    val sharers = snapshot.browse

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${peers.size} client(s) online", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBrowseRefresh) { Text("Refresh") }
        }

        if (peers.isEmpty() && sharers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No one else is here yet.\nShare the router and passphrase with your friends.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers) { peer ->
                    Card(colors = CardDefaults.cardColors(containerColor = DsSurface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(parseHex(peer.color), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(peer.name.ifBlank { "unknown" }, modifier = Modifier.weight(1f))
                            if (peer.mute) {
                                Text("muted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                            }
                            TextButton(onClick = { onPm(peer.id, peer.name) }) { Text("PM") }
                            TextButton(onClick = { onMute(peer.id, !peer.mute) }) { Text(if (peer.mute) "Unmute" else "Mute") }
                        }
                    }
                }

                if (sharers.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Sharing", fontWeight = FontWeight.SemiBold, color = DsBlue)
                    }
                }
                items(sharers) { sharer ->
                    Card(colors = CardDefaults.cardColors(containerColor = DsSurfaceHigh)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onBrowse(sharer.key, sharer.name) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = parseHex("0c9bdc"))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sharer.name, fontWeight = FontWeight.SemiBold)
                                Text(formatSize(sharer.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Browse", fontSize = 13.sp, color = DsBlue)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PmScreen(peer: Peer?, onBack: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chat = peer?.pm ?: emptyList()

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(parseHex(peer?.color ?: ""), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(peer?.name ?: "Private message", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DsSurface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (chat.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Start a private conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chat) { message ->
                        MessageBubble(message, mine = message.name != peer?.name)
                    }
                }
            }

            MessageInput(draft = draft, onDraft = { draft = it }, onSend = {
                if (it.isNotBlank()) {
                    onSend(it)
                    draft = ""
                }
            })
        }
    }
}

// ---------------------------------------------------------------------------
// Browse
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseScreen(
    key: String,
    title: String,
    snapshot: Snapshot,
    onBack: () -> Unit,
    onOpen: (String, String) -> Unit,
    onDownload: (String) -> Unit,
    onDownloadAll: (String) -> Unit,
    onBrowse: (String) -> Unit,
) {
    val entries = snapshot.nodes[key] ?: emptyList()

    // Auto-load the contents when the folder is opened
    LaunchedEffect(key) { onBrowse(key) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${entries.size} item(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onDownloadAll(key) }) { Icon(Icons.Filled.CloudDownload, "Download all") }
                    IconButton(onClick = { onBrowse(key) }) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DsSurface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Empty share.\nTap refresh to load the contents.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entries) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.type == "folder") {
                                        onBrowse(entry.key)
                                        onOpen(entry.key, entry.name)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (entry.type == "folder") {
                                Icon(Icons.Filled.Folder, null, tint = Color(0xFFD9A441))
                            } else {
                                Icon(Icons.Filled.InsertDriveFile, null, tint = DsBlue)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1)
                                Text(
                                    "${formatSize(entry.size)} · ${formatTime(entry.time)}".trim(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (entry.type == "file") {
                                IconButton(onClick = { onDownload(entry.key) }) {
                                    Icon(Icons.Filled.CloudDownload, "Download", tint = DsBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Transfers
// ---------------------------------------------------------------------------

@Composable
private fun TransfersPane(snapshot: Snapshot, vm: AppViewModel) {
    var section by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section, containerColor = DsSurface) {
            Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("Downloads") })
            Tab(selected = section == 1, onClick = { section = 1 }, text = { Text("Uploads") })
            Tab(selected = section == 2, onClick = { section = 2 }, text = { Text("Finished") })
            Tab(selected = section == 3, onClick = { section = 3 }, text = { Text("Log") })
        }

        when (section) {
            0 -> TransferList(
                transfers = snapshot.downloads,
                empty = "No downloads queued.\nBrowse a peer and tap a file.",
                onCancel = vm::cancelDownload,
            )
            1 -> TransferList(
                transfers = snapshot.uploads,
                empty = "No uploads yet.\nUploads happen when peers download your shares.",
                onCancel = vm::cancelUpload,
            )
            2 -> {
                if (snapshot.finished.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing finished yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(snapshot.finished) { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.done) Icons.Filled.InsertDriveFile else Icons.Filled.Close,
                                    null,
                                    tint = if (item.done) DsGreen else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(item.name, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(formatSize(item.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            else -> {
                if (snapshot.errors.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(snapshot.errors) { error ->
                            Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferList(transfers: List<Transfer>, empty: String, onCancel: (String) -> Unit) {
    if (transfers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(empty, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(transfers) { transfer ->
            Card(colors = CardDefaults.cardColors(containerColor = DsSurface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (transfer.done) Icons.Filled.InsertDriveFile else Icons.Filled.CloudDownload,
                            null,
                            tint = if (transfer.done) DsGreen else DsBlue,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(transfer.name, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onCancel(transfer.id) }) { Text("Cancel") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (transfer.percent / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${String.format("%.0f", transfer.percent)}% of ${formatSize(transfer.size)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (transfer.running) "${formatSize(transfer.speed)}/s ${transfer.eta}".trim() else transfer.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPane(snapshot: Snapshot, vm: AppViewModel) {
    val context = LocalContext.current
    var sharePath by remember { mutableStateOf("") }
    var downloadPath by remember { mutableStateOf(snapshot.downloadPath) }
    var searchText by remember { mutableStateOf("") }

    val hasAllFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Community", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Text(snapshot.groupLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Share this group with your friends. They join by using the same router and passphrase.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = DsSurfaceHigh)
        Spacer(Modifier.height(16.dp))

        Text("Search shared files", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Keywords, e.g. ubuntu iso") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.search(searchText) }, modifier = Modifier.size(44.dp).background(DsBlue, CircleShape)) {
                Icon(Icons.Filled.Search, "Search", tint = Color.White)
            }
        }
        if (snapshot.searchResults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            snapshot.searchResults.take(20).forEach { result ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (result.type == "folder") Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        null,
                        tint = if (result.type == "folder") Color(0xFFD9A441) else DsBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(result.name, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(formatSize(result.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (result.type == "file") {
                        TextButton(onClick = { vm.download(result.key) }) { Text("Get") }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = DsSurfaceHigh)
        Spacer(Modifier.height(16.dp))

        Text("My shares", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))

        if (!hasAllFiles) {
            Card(colors = CardDefaults.cardColors(containerColor = DsSurfaceHigh)) {
                Column(Modifier.padding(12.dp)) {
                    Text("File access", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android blocks apps from reading folders outside their own storage. Grant \"All files access\" to share or save anywhere on the device.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }) {
                        Text("Grant all files access")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = sharePath,
                onValueChange = { sharePath = it },
                placeholder = { Text("/storage/emulated/0/Movies") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                vm.shareAdd(sharePath.trim())
                sharePath = ""
            }) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }
        Spacer(Modifier.height(8.dp))

        snapshot.shares.forEach { share ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (share.type == "folder") Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                    null,
                    tint = if (share.type == "folder") Color(0xFFD9A441) else DsBlue,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(share.name, maxLines = 1)
                    Text("${formatSize(share.size)} · ${share.path}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { vm.shareRemove(share.path) }) {
                    Icon(Icons.Filled.Close, "Remove share", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (snapshot.shares.isEmpty()) {
            Text("You are not sharing anything.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = DsSurfaceHigh)
        Spacer(Modifier.height(16.dp))

        Text("Downloads", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = downloadPath,
                onValueChange = { downloadPath = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text("Save to") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.setDownloadPath(downloadPath.trim()) }) { Text("Apply") }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = DsSurfaceHigh)
        Spacer(Modifier.height(16.dp))

        Text("Client", fontWeight = FontWeight.SemiBold, color = DsBlue)
        Spacer(Modifier.height(8.dp))
        Text(snapshot.name.ifBlank { "—" })
        Text("Status: ${snapshot.status}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))

        Button(onClick = vm::join, modifier = Modifier.fillMaxWidth()) {
            Text("Join group")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}
