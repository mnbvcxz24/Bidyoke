package com.bidyoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF100E16)
private val Panel = Color(0xFF1D1927)
private val Raised = Color(0xFF292336)
private val Violet = Color(0xFFB99AFF)
private val Muted = Color(0xFFAAA1B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BidyokeTheme { BidyokeApp() } }
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

private enum class Destination(val label: String) {
    HOME("Home"), LIBRARY("Songbook"), PLAYER("Karaoke"), SETTINGS("Settings")
}
private data class Song(val number: String, val title: String, val artist: String)
private val sampleSongs = listOf(
    Song("00125", "Nothing's Gonna Change My Love for You", "George Benson"),
    Song("00126", "Through the Years", "Kenny Rogers"),
    Song("00127", "Bakit Ngayon Ka Lang", "Ogie Alcasid"),
    Song("00128", "Wonderful Tonight", "Eric Clapton"),
    Song("00129", "Torete", "Moonstar88")
)

@Composable
private fun BidyokeApp() {
    var destination by remember { mutableStateOf(Destination.HOME) }
    var songNumber by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf(listOf(sampleSongs[1], sampleSongs[3])) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wide) {
                NavigationRail(containerColor = Panel, contentColor = Violet) {
                    Spacer(Modifier.height(18.dp))
                    Text("B", Modifier.align(Alignment.CenterHorizontally), color = Violet, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(20.dp))
                    Destination.entries.forEach { item ->
                        NavigationRailItem(destination == item, { destination = item }, icon = { DestinationIcon(item) }, label = { Text(item.label) })
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BIDYOKE", color = Violet, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 20.sp)
                        Text("Your Stage. Your Songs.", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Raised, shape = CircleShape) {
                        Text("● READY", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF8EE0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                when (destination) {
                    Destination.HOME -> HomeScreen(songNumber, { songNumber = it }, queue, { queue = queue + it }, { destination = Destination.PLAYER })
                    Destination.LIBRARY -> LibraryScreen { song -> queue = queue + song }
                    Destination.PLAYER -> KaraokeScreen()
                    Destination.SETTINGS -> SettingsScreen()
                }
            }
        }
        if (!wide) {
            NavigationBar(containerColor = Panel) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(destination == item, { destination = item }, icon = { DestinationIcon(item) }, label = { Text(item.label) })
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
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(queue) { song -> SongRow(song) {} } }
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
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SONGBOOK", color = Violet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Browse by song number, title, or artist. These are sample entries for now.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sampleSongs) { song -> SongRow(song) { onAdd(song) } } }
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
                    Text("Your song video will appear here", color = Muted)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("I found a love for me", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Darling, just dive right in", color = Violet, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("SYNCHRONIZED LYRICS PREVIEW", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS", color = Violet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        SectionCard("SONG LIBRARY") {
            Text("Choose a folder where your karaoke files are stored.", color = Muted)
            Text("Folder selection and persistent access will be wired in the next setup step.", color = Muted, fontSize = 12.sp)
        }
        SectionCard("AUDIO & DISPLAY") {
            Text("Microphone, speaker output, lyrics appearance, and TV display settings will be configured in later phases.", color = Muted)
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
