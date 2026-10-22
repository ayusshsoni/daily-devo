package com.example.dailydevo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.dailydevo.data.*
import com.example.dailydevo.domain.TranslationResolver
import com.example.dailydevo.wallpaper.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailyDevoTheme { DailyDevoRoot() } }
    }

    override fun onResume() {
        super.onResume()
        (application as DailyDevoApp).recoverMissedUpdate()
    }
}

class DailyDevoViewModel(app: DailyDevoApp) : AndroidViewModel(app) {
    private val dao = app.database.dao()
    private val prefs = app.preferences
    val verses = dao.allVerses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())
    val history = dao.latestHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun language(code: String) = viewModelScope.launch { prefs.language(code) }
    fun automatic(value: Boolean) = viewModelScope.launch { prefs.automatic(value) }
    fun target(value: String) = viewModelScope.launch { prefs.target(value) }
    fun dark(value: Boolean) = viewModelScope.launch { prefs.dark(value) }
    fun gitaEnabled(value: Boolean) = viewModelScope.launch { prefs.gitaEnabled(value) }
    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (favorites.value.any { it.verseId == id }) dao.removeFavorite(id)
        else dao.saveFavorite(FavoriteEntity(id, System.currentTimeMillis()))
    }
    suspend fun content(verse: VerseEntity): WallpaperContent {
        val language = preferences.value.language
        val translation = if (language == "sa") null else dao.translation(verse.id, language)
        val resolved = TranslationResolver.resolve(verse, language, translation)
        return WallpaperContent(verse, resolved.text, language, resolved.sourceNotice)
    }
    suspend fun apply(verse: VerseEntity): String = withContext(Dispatchers.IO) {
        val prefsNow = preferences.value
        val bitmap = getApplication<DailyDevoApp>().renderer.render(content(verse), prefsNow.darkWallpaper)
        val result = getApplication<DailyDevoApp>().applier.apply(bitmap, prefsNow.target)
        bitmap.recycle()
        val ok = result is ApplyResult.Applied
        val message = if (result is ApplyResult.Failed) result.reason else "Wallpaper applied successfully"
        val today = LocalDate.now().toString()
        dao.saveHistory(WallpaperHistoryEntity(today, "manual", verse.id, prefsNow.target, if (ok) "success" else "failure", message, System.currentTimeMillis()))
        message
    }
}

private val paper = Color(0xFFF6F1E8)
private val ink = Color(0xFF2C3131)
private val gold = Color(0xFF9E7135)

@Composable
private fun DailyDevoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = gold, onPrimary = Color.White, background = paper, surface = Color(0xFFFFFCF7), onSurface = ink), content = content)
}

@Composable
private fun DailyDevoRoot() {
    val app = LocalContext.current.applicationContext as DailyDevoApp
    val factory = remember(app) { object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = DailyDevoViewModel(app) as T
    } }
    val vm: DailyDevoViewModel = viewModel(factory = factory)
    val nav = rememberNavController()
    val entries = listOf("today" to "Today", "library" to "Library", "favorites" to "Saved", "settings" to "Settings")
    Scaffold(containerColor = paper, bottomBar = {
        NavigationBar(containerColor = Color(0xFFFFFCF7)) {
            entries.forEach { (route, label) ->
                NavigationBarItem(selected = nav.currentBackStackEntryAsState().value?.destination?.route == route, onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo("today") } }, icon = { Text(when (route) { "today" -> "◦"; "library" -> "▤"; "favorites" -> "♡"; else -> "⚙" }, fontSize = 20.sp) }, label = { Text(label) })
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(vm, onPreview = { nav.navigate("preview/${it.id}") }) }
            composable("library") { VerseListScreen(vm, title = "Library", onSelect = { nav.navigate("detail/${it.id}") }) }
            composable("favorites") {
                val all by vm.verses.collectAsState()
                val saved by vm.favorites.collectAsState()
                val chosen = remember(all, saved) { all.filter { verse -> saved.any { it.verseId == verse.id } } }
                VerseListScreen(vm, title = "Saved verses", values = chosen, onSelect = { nav.navigate("detail/${it.id}") })
            }
            composable("settings") { SettingsScreen(vm) }
            composable("preview/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val verses by vm.verses.collectAsState()
                verses.firstOrNull { it.id == entry.arguments?.getString("id") }?.let { PreviewScreen(vm, it) } ?: EmptyScreen("Verse not found")
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val verses by vm.verses.collectAsState()
                verses.firstOrNull { it.id == entry.arguments?.getString("id") }?.let { DetailScreen(vm, it) } ?: EmptyScreen("Verse not found")
            }
        }
    }
}

@Composable
private fun TodayScreen(vm: DailyDevoViewModel, onPreview: (VerseEntity) -> Unit) {
    val verses by vm.verses.collectAsState()
    val prefs by vm.preferences.collectAsState()
    val history by vm.history.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val verse = remember(verses, prefs.gitaEnabled) { if (!prefs.gitaEnabled || verses.isEmpty()) null else verses.filter { it.collection == "Bhagavad Gita" }.let { eligible -> eligible.getOrNull(Math.floorMod(LocalDate.now().toEpochDay(), eligible.size.toLong()).toInt()) } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("DAILY DEVO", color = gold, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        Text("A little stillness,\nevery day.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = ink)
        Text(LocalDate.now().toString(), color = Color.DarkGray)
        if (verse == null) Text(if (prefs.gitaEnabled) "No content is available. Please check the bundled library." else "No collection is enabled. Turn on Bhagavad Gita in Settings.") else {
            val languageText = verseText(verse, vm, prefs.language)
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (prefs.darkWallpaper) Color(0xFF19222C) else Color(0xFFFFFCF7))) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(verse.collection.uppercase(), color = gold, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelMedium)
                    Text(languageText, color = if (prefs.darkWallpaper) paper else ink, style = MaterialTheme.typography.titleLarge, lineHeight = 34.sp)
                    Text("${verse.source} ${verse.reference}", color = gold, style = MaterialTheme.typography.bodyMedium)
                    Text(verse.meaning, color = if (prefs.darkWallpaper) paper.copy(alpha = .75f) else ink.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onPreview(verse) }, modifier = Modifier.weight(1f)) { Text("Preview") }
                OutlinedButton(onClick = { scope.launch { message = vm.apply(verse) } }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
            TranslationNotice(vm, verse, prefs.language)
            TextButton(onClick = { vm.toggleFavorite(verse.id) }) { Text(if (favorites.any { it.verseId == verse.id }) "♥ Saved" else "♡ Save verse") }
        }
        message?.let { Text(it, color = gold) }
        Text("Last wallpaper: ${history?.let { "${it.status} · ${it.updatedAtEpochMillis.let { millis -> java.text.DateFormat.getDateTimeInstance().format(java.util.Date(millis)) }}" } ?: "Not applied yet"}", style = MaterialTheme.typography.bodySmall)
        Text("Automatic updates run in a morning window. Android may delay background work.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun verseText(verse: VerseEntity, vm: DailyDevoViewModel, language: String): String {
    var text by remember(verse.id, language) { mutableStateOf(verse.originalText) }
    LaunchedEffect(verse.id, language) { text = vm.content(verse).text }
    return text
}

@Composable
private fun TranslationNotice(vm: DailyDevoViewModel, verse: VerseEntity, language: String) {
    var notice by remember(verse.id, language) { mutableStateOf<String?>(null) }
    LaunchedEffect(verse.id, language) { notice = vm.content(verse).sourceNotice }
    notice?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun VerseListScreen(vm: DailyDevoViewModel, title: String, values: List<VerseEntity>? = null, onSelect: (VerseEntity) -> Unit) {
    val fetched by vm.verses.collectAsState()
    val list = values ?: fetched
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 18.dp))
        if (list.isEmpty()) Text(if (title == "Saved verses") "Save a verse from Today to find it here." else "Your offline library is ready shortly.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { verse ->
                val text = verseText(verse, vm, vm.preferences.collectAsState().value.language)
                Card(onClick = { onSelect(verse) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${verse.source} ${verse.reference}", color = gold, style = MaterialTheme.typography.labelMedium)
                        Text(text, maxLines = 3, style = MaterialTheme.typography.bodyLarge, color = ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(vm: DailyDevoViewModel, verse: VerseEntity) {
    val prefs by vm.preferences.collectAsState()
    val text = verseText(verse, vm, prefs.language)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("${verse.source} ${verse.reference}", color = gold, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.headlineSmall, lineHeight = 34.sp)
        Text("Original text", fontWeight = FontWeight.Bold)
        Text(verse.originalText, style = MaterialTheme.typography.titleMedium, lineHeight = 30.sp)
        Text("Reflection", fontWeight = FontWeight.Bold)
        Text(verse.meaning)
        Text("Translation is an editorial draft and needs review.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { vm.toggleFavorite(verse.id) }) { Text("Save / remove from saved") }
    }
}

@Composable
private fun PreviewScreen(vm: DailyDevoViewModel, verse: VerseEntity) {
    val prefs by vm.preferences.collectAsState()
    val app = LocalContext.current.applicationContext as DailyDevoApp
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(verse.id, prefs.language, prefs.darkWallpaper) {
        val content = vm.content(verse)
        bitmap = withContext(Dispatchers.Default) { app.renderer.render(content, prefs.darkWallpaper) }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wallpaper preview", style = MaterialTheme.typography.headlineSmall)
        bitmap?.let { Image(it.asImageBitmap(), "Generated spiritual wallpaper", modifier = Modifier.weight(1f).fillMaxWidth(.68f), contentScale = androidx.compose.ui.layout.ContentScale.Fit) } ?: CircularProgressIndicator()
        Text("${verse.source} ${verse.reference}", color = gold)
        TranslationNotice(vm, verse, prefs.language)
        Button(onClick = { scope.launch { message = vm.apply(verse) } }) { Text("Apply to ${prefs.target}") }
        message?.let { Text(it, color = gold) }
    }
}

@Composable
private fun SettingsScreen(vm: DailyDevoViewModel) {
    val prefs by vm.preferences.collectAsState()
    val history by vm.history.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Display language", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en" to "English", "hi" to "हिन्दी", "gu" to "ગુજરાતી", "sa" to "संस्कृत").forEach { (code, label) ->
                FilterChip(selected = prefs.language == code, onClick = { vm.language(code) }, label = { Text(label) })
            }
        }
        Text("Wallpaper target", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("home" to "Home", "lock" to "Lock", "both" to "Both").forEach { (value, label) ->
                FilterChip(selected = prefs.target == value, onClick = { vm.target(value) }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("Automatic daily change", fontWeight = FontWeight.Bold); Text("Morning-oriented; Android may delay it.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = prefs.automatic, onCheckedChange = { vm.automatic(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark wallpaper style", modifier = Modifier.weight(1f)); Switch(checked = prefs.darkWallpaper, onCheckedChange = { vm.dark(it) })
        }
        Text("Enabled collections", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bhagavad Gita · 2 starter verses", modifier = Modifier.weight(1f))
            Switch(checked = prefs.gitaEnabled, onCheckedChange = { vm.gitaEnabled(it) })
        }
        Text("Last update: ${history?.message ?: "No wallpaper update recorded"}", style = MaterialTheme.typography.bodySmall)
        Text("WorkManager provides persistent, deferrable work; Android, battery settings and device manufacturers may delay execution. The app checks again when opened. For reliable updates, keep automatic changes enabled and open the app after a missed day.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text("Starter translations are editorial drafts and should be reviewed before distribution.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun EmptyScreen(message: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) } }
