package com.bidyoke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val Ink = Color(0xFF100E16)
private val Panel = Color(0xFF1D1927)
private val Raised = Color(0xFF292336)
private val Violet = Color(0xFFB99AFF)
private val Muted = Color(0xFFAAA1B8)
private const val PREFS_NAME = "bidyoke_preferences"
private const val PREF_SONG_FOLDER = "song_folder_uri"

class MainActivity : ComponentActivity() {
    private var remoteKeyHandler: ((android.view.KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installBundledDemoSongs()
        setContent {
            BidyokeTheme {
                BidyokeApp(onRemoteKeyHandler = { remoteKeyHandler = it })
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (remoteKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun BidyokeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, onPrimary = Ink, secondary = Color(0xFFD7C6FF),
            background = Ink, surface = Panel, surfaceVariant = Raised,
            onBackground = Color(0xFFF5F0FC), onSurface = Color(0xFFF5F0FC),
            onSurfaceVariant = Muted
        ),
        content = content
    )
}


private fun MainActivity.installBundledDemoSongs() {
    val assetNames = assets.list("demo_songs") ?: return
    val targetDir = File(filesDir, "demo_songs").apply { mkdirs() }
    assetNames.filter { it.endsWith(".kar", ignoreCase = true) }.forEach { name ->
        val target = File(targetDir, name)
        if (!target.exists()) {
            assets.open("demo_songs/$name").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

private enum class Destination(val label: String) {
    HOME("Home"), LIBRARY("Songbook"), PLAYER("Karaoke"), SETTINGS("Settings")
}
private data class Song(val number: String, val title: String, val artist: String, val fileName: String? = null)
private val sampleSongs = listOf(
    Song("00125", "Midnight Drive", "Bidyoke Demo Studio", "00125_midnight_drive.kar"),
    Song("00126", "Bituing Gabay", "Bidyoke Demo Studio", "00126_bituing_gabay.kar"),
    Song("00127", "Summer Lights", "Bidyoke Demo Studio", "00127_summer_lights.kar"),
    Song("00128", "Awit ng Umaga", "Bidyoke Demo Studio", "00128_awit_ng_umaga.kar")
)

@Composable
private fun BidyokeApp(onRemoteKeyHandler: ((android.view.KeyEvent) -> Boolean) -> Unit) {
    val context = LocalContext.current
    var overlay by remember { mutableStateOf<Destination?>(null) }
    var songNumber by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf(emptyList<Song>()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var currentLyric by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    val midiPlayer = remember { MidiKaraokePlayer(context) { playing, lyric -> isPlaying = playing; currentLyric = lyric; if (!playing) currentSong = null } }
    DisposableEffect(midiPlayer) { onDispose { midiPlayer.release() } }
    fun startSong(song: Song) {
        val file = song.fileName?.let { File(context.filesDir, "demo_songs/$it") }
        if (file != null && file.exists()) { currentSong = song; currentLyric = ""; isPlaying = true; midiPlayer.play(file) }
    }
    var songFolder by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, 0).getString(PREF_SONG_FOLDER, null))
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                songFolder = uri.toString()
                context.getSharedPreferences(PREFS_NAME, 0).edit().putString(PREF_SONG_FOLDER, uri.toString()).apply()
            } catch (_: SecurityException) { songFolder = null }
        }
    }
    val matchedSong = sampleSongs.firstOrNull { it.number == songNumber }
    val keyHandler: (android.view.KeyEvent) -> Boolean = { event ->
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                    val digit = (event.keyCode - android.view.KeyEvent.KEYCODE_0).toString()
                    if (songNumber.length < 5) songNumber += digit
                    true
                }
                android.view.KeyEvent.KEYCODE_DEL -> { songNumber = songNumber.dropLast(1); true }
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (matchedSong != null) { if (currentSong == null) startSong(matchedSong) else queue = queue + matchedSong; songNumber = "" }
                    true
                }
                android.view.KeyEvent.KEYCODE_MENU, android.view.KeyEvent.KEYCODE_SETTINGS -> {
                    overlay = if (overlay == null) Destination.LIBRARY else null
                    true
                }
                android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_ESCAPE -> {
                    if (overlay != null) { overlay = null; true } else false
                }
                else -> false
            }
        } else false
    }
    SideEffect { onRemoteKeyHandler(keyHandler) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Traditional karaoke standby screen: video area is the whole experience.
        Box(
            Modifier.fillMaxSize().clickable { if (overlay == null) overlay = Destination.LIBRARY },
            contentAlignment = Alignment.Center
        ) {
            if (currentSong != null && isPlaying) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("NOW PLAYING", color = Violet, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(currentSong?.title.orEmpty(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    Text(currentLyric.ifBlank { "♪  ♪  ♪" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("BIDYOKE • MIDI KARAOKE", color = Muted, fontSize = 11.sp)
                }
            } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BIDYOKE", color = Violet.copy(alpha = .75f), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                Text(
                    songNumber.padStart(5, '0').ifEmpty { "00000" },
                    color = Color.White,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 12.sp
                )
                Text(
                    if (songNumber.isEmpty()) "ENTER SONG NUMBER" else matchedSong?.title ?: "ENTER SONG NUMBER",
                    color = if (matchedSong != null) Violet else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                if (matchedSong != null) Text("Press ENTER to play", color = Muted, fontSize = 12.sp)
            }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("READY", color = Color(0xFF8EE0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${queue.size} SONGS IN QUEUE", color = Muted, fontSize = 11.sp)
                }
                FilledTonalButton(onClick = { overlay = Destination.LIBRARY }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("MENU")
                }
            }
        }

        if (overlay != null) {
            Row(Modifier.fillMaxSize().background(Ink.copy(alpha = .97f))) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("BIDYOKE MENU", color = Violet, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 20.sp)
                            Text("Select an option", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { overlay = null }) { Text("CLOSE  ✕") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Destination.entries.forEach { item ->
                            FilterChip(
                                selected = overlay == item,
                                onClick = { overlay = item },
                                label = { Text(item.label) },
                                leadingIcon = { DestinationIcon(item) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    when (overlay) {
                        Destination.HOME -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("ENTER SONG NUMBER", color = Violet, fontWeight = FontWeight.Bold)
                            Text(songNumber.padStart(5, '0').ifEmpty { "00000" }, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                            listOf("1","2","3","4","5","6","7","8","9","⌫","0","CLEAR").chunked(3).forEach { keys ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    keys.forEach { key ->
                                        KeyButton(key, Modifier.weight(1f)) {
                                            when (key) {
                                                "⌫" -> songNumber = songNumber.dropLast(1)
                                                "CLEAR" -> songNumber = ""
                                                else -> if (songNumber.length < 5) songNumber += key
                                            }
                                        }
                                    }
                                }
                            }
                            Button(onClick = { matchedSong?.let { queue = queue + it; songNumber = "" } }, enabled = matchedSong != null, modifier = Modifier.fillMaxWidth()) {
                                Text("ADD TO QUEUE")
                            }
                        }
                        Destination.LIBRARY -> LibraryScreen { song -> queue = queue + song }
                        Destination.PLAYER -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("NOW PLAYING", color = Violet, fontWeight = FontWeight.Bold)
                            Text("Playback controls and synchronized lyrics will appear here when the playback engine is connected.", color = Muted)
                            Text("Queue: ${queue.size} songs", color = Color.White)
                        }
                        Destination.SETTINGS -> SettingsScreen(
                            folderUri = songFolder,
                            onChooseFolder = { folderPicker.launch(null) },
                            onClearFolder = {
                                songFolder?.let { saved ->
                                    try { context.contentResolver.releasePersistableUriPermission(Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    catch (_: SecurityException) { }
                                }
                                songFolder = null
                                context.getSharedPreferences(PREFS_NAME, 0).edit().remove(PREF_SONG_FOLDER).apply()
                            }
                        )
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination) {
    val icon = when (destination) {
        Destination.HOME -> Icons.Default.Home
        Destination.LIBRARY -> Icons.Default.LibraryMusic
        Destination.PLAYER -> Icons.Default.Mic
        Destination.SETTINGS -> Icons.Default.Settings
    }
    Icon(icon, contentDescription = destination.label)
}

@Composable
private fun HomeScreen(number: String, onNumberChange: (String) -> Unit, queue: List<Song>, onAdd: (Song) -> Unit, onOpenPlayer: () -> Unit) {
    val match = sampleSongs.firstOrNull { it.number == number }
    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("ENTER SONG NUMBER", Modifier.weight(1.1f)) {
                Text("Choose a song by its number", color = Muted, fontSize = 13.sp)
                Surface(color = Ink, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = number,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) onNumberChange(it) },
                        modifier = Modifier.padding(13.dp).fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = Violet, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { inner ->
                            if (number.isEmpty()) Text("00000", color = Muted.copy(alpha = .55f), fontSize = 28.sp)
                            inner()
                        }
                    )
                }
                listOf("1","2","3","4","5","6","7","8","9","⌫","0","CLEAR").chunked(3).forEach { keys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        keys.forEach { key ->
                            KeyButton(key, Modifier.weight(1f)) {
                                when (key) {
                                    "⌫" -> onNumberChange(number.dropLast(1))
                                    "CLEAR" -> onNumberChange("")
                                    else -> if (number.length < 5) onNumberChange(number + key)
                                }
                            }
                        }
                    }
                }
                Button(onClick = { match?.let(onAdd) }, enabled = match != null, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text(if (match == null) "ENTER A VALID NUMBER" else "ADD SONG TO QUEUE")
                }
            }
            SectionCard("QUICK PICKS", Modifier.weight(.8f)) {
                sampleSongs.take(3).forEach { song -> SongRow(song) { onAdd(song) } }
            }
        }
        SectionCard("IN QUEUE • ${queue.size} SONGS", Modifier.weight(1f).fillMaxHeight()) {
            if (queue.isEmpty()) Text("No song requests yet. Enter a song number to get started.", color = Muted)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(queue) { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SongRow(song) {} }
                        IconButton(onClick = { /* queue editing is intentionally host-only in a later milestone */ }) {
                            Icon(Icons.Default.QueueMusic, "Queued song", tint = Violet)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onOpenPlayer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("OPEN KARAOKE SCREEN")
            }
        }
    }
}

@Composable
private fun LibraryScreen(onAdd: (Song) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = sampleSongs.filter {
        it.number.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true) ||
            it.artist.contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SONGBOOK", color = Violet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Four original offline KAR demos are bundled with Bidyoke for testing. Their MIDI notes and synchronized lyric events are included in each file.", color = Muted)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search song number, title, or artist") }, singleLine = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { song -> SongRow(song) { onAdd(song) } }
        }
        if (filtered.isEmpty()) Text("No matching songs.", color = Muted)
    }
}

@Composable
private fun KaraokeScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, null, tint = Violet, modifier = Modifier.size(54.dp))
                    Text("KARAOKE VIDEO", color = Violet, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Video playback will be connected in a later phase.", color = Muted)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LYRICS PREVIEW", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("I found a love for me", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Darling, just dive right in", color = Violet, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SettingsScreen(folderUri: String?, onChooseFolder: () -> Unit, onClearFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS", color = Violet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        SectionCard("SONG LIBRARY") {
            Text("Select the folder containing your karaoke media. Bidyoke keeps read access after restarting the app.", color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FolderOpen, null, tint = Violet)
                Text(if (folderUri == null) "No folder selected" else Uri.parse(folderUri).lastPathSegment ?: "Folder selected", color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(if (folderUri == null) "CHOOSE SONG FOLDER" else "CHANGE SONG FOLDER")
            }
            if (folderUri != null) {
                TextButton(onClick = onClearFolder) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear selected folder")
                }
            }
        }
        SectionCard("BUILT-IN TEST SONGS") {
            Text("4 original KAR demo files are included and copied into Bidyoke's private storage for offline testing.", color = Muted)
            sampleSongs.forEach { song ->
                Text("${song.number}  •  ${song.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        SectionCard("AUDIO & DISPLAY") {
            Text("Microphone mixing, speaker output, lyrics appearance, and TV display controls are planned for later phases.", color = Muted)
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = if (focused) Violet.copy(alpha = .25f) else Raised,
        shape = RoundedCornerShape(10.dp),
        border = if (focused) BorderStroke(1.dp, Violet) else null,
        modifier = modifier.height(42.dp).onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (label == "CLEAR") Violet else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(song.number, color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.QueueMusic, null, tint = Violet, modifier = Modifier.size(18.dp))
    }
}
