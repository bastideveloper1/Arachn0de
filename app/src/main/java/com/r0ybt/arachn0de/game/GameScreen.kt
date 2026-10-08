package com.r0ybt.arachn0de.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R
import kotlinx.coroutines.delay

internal val ParchmentInk = Color(0xFF34200E)

@Composable
internal fun GameScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { context.getSharedPreferences("experimental_game", android.content.Context.MODE_PRIVATE) }
    val map = FirstGameMap.value
    var snapshot by rememberSaveable { mutableStateOf(storage.getString("session", "") ?: "") }
    var page by rememberSaveable { mutableStateOf("home") }
    val session = remember(snapshot) { GameSessionCodec.decode(snapshot, map) }
    fun update(s: GameSession) { snapshot = GameSessionCodec.encode(s) }
    // Always read the latest authoritative phase, including two taps before recomposition.
    fun act(action: (GameSession) -> GameSession) {
        val latest = GameSessionCodec.decode(snapshot, map) ?: return
        try { update(action(latest)) } catch (_: IllegalArgumentException) { /* Stale/disabled action. */ }
    }
    LaunchedEffect(snapshot) { storage.edit().putString("session", snapshot).apply() }
    LaunchedEffect(page, snapshot) {
        if (page == "board" && session?.phase == TurnPhase.MOVING) {
            delay(300)
            act { if (it.phase == TurnPhase.MOVING) GameRules.step(it, map) else it }
        }
    }
    fun back() { if (page == "home") onBack() else page = "home" }
    BackHandler { back() }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            val castle = page != "board" || session?.phase in setOf(TurnPhase.HANDOFF, TurnPhase.WON)
            if (castle) {
                Image(painterResource(R.drawable.fondocastillo), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { back() }) { Text("Volver", color = if (castle) Color.White else MaterialTheme.colorScheme.onSurface) }
                    Text("JUEGO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (castle) Color.White else MaterialTheme.colorScheme.onSurface)
                }
                when {
                    page == "setup" -> GameSetup { names, characters, laps -> update(GameRules.newGame(names, characters, map, laps)); page = "board" }
                    page == "board" && session != null -> when (session.phase) {
                        TurnPhase.HANDOFF -> GameHandoff(session, onBegin = { act(GameRules::beginTurn) }, onHome = { page = "home" })
                        TurnPhase.WON -> GameResults(session) { page = "home" }
                        else -> GameBoard(session, map, onRoll = { act { GameRules.roll(it, GameRules.rollD6()) } },
                            onAbility = { act(GameRules::startPlacement) }, onTile = { id -> act { GameRules.selectPlacement(it, map, id) } },
                            onConfirm = { act { GameRules.confirmPlacement(it, map) } }, onCancel = { act(GameRules::cancelPlacement) })
                    }
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        GameArtButton("Nueva partida", onClick = { page = "setup" })
                        if (session != null) GameArtButton(if (session.phase == TurnPhase.WON) "Ver resultado" else "Continuar partida", onClick = { page = "board" })
                    }
                }
            }
            if (page == "board" && session != null) {
                when (session.phase) {
                    TurnPhase.ROLLING -> DiceOverlay(requireNotNull(session.lastRoll)) { act(GameRules::finishRoll) }
                    TurnPhase.DAMAGE -> GameRules.currentDamage(session)?.let { event ->
                        val attacker = session.players.first { it.id == event.attackerId }
                        key(event.id) { DamageOverlay(event, attacker) { act(GameRules::acknowledgeDamage) } }
                    }
                    TurnPhase.REST -> GameRestOverlay(session.currentPlayer) { act(GameRules::rest) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun ParchmentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier) {
        Image(painterResource(R.drawable.pergamino), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), content = content)
    }
}

@Composable
internal fun GameArtButton(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ParchmentPanel(modifier.padding(vertical = 3.dp).heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        Text(label, color = ParchmentInk, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun GameSetup(onStart: (List<String>, List<RatCharacter>, Int) -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var laps by rememberSaveable { mutableIntStateOf(1) }
    var names by rememberSaveable { mutableStateOf(listOf("Jugador 1", "Jugador 2", "Jugador 3", "Jugador 4")) }
    var choices by rememberSaveable { mutableStateOf(listOf("KNIGHT", "MAGE", "HUNTRESS", "NECROMANCER")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cantidad de jugadores", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }, modifier = Modifier.weight(1f).testTag("players-$n").semantics { contentDescription = "$n jugadores" }) }
        }
        Text("Duración de la partida (vueltas)", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..4).forEach { n -> FilterChip(selected = laps == n, onClick = { laps = n }, label = { Text("$n") }, modifier = Modifier.weight(1f).testTag("laps-$n").semantics { contentDescription = "$n vueltas" }) }
        }
        (0 until count).forEach { index ->
            Text("Jugador ${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = names[index], onValueChange = { value -> names = names.mapIndexed { i, name -> if (i == index) value.take(16) else name } },
                label = { Text("Nombre (máximo 16 caracteres)") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White, unfocusedLabelColor = Color.White), modifier = Modifier.fillMaxWidth())
            RatCharacter.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { character ->
                        val selected = choices[index] == character.name
                        val taken = (0 until count).any { it != index && choices[it] == character.name }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .border(if (selected) 2.dp else 0.dp, if (selected) Color(0xFFFFCD6A) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable(enabled = !taken) { choices = choices.mapIndexed { i, name -> if (i == index) character.name else name } }
                            .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(character.art().normalSprite), character.label, Modifier.size(76.dp), contentScale = ContentScale.Fit, alpha = if (taken) .35f else 1f)
                            Text(character.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text(if (taken) "Ocupado" else if (selected) "Elegido" else "Elegir", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        GameArtButton("Comenzar partida", enabled = names.take(count).all { it.trim().isNotEmpty() } && choices.take(count).distinct().size == count,
            modifier = Modifier.fillMaxWidth()) { onStart(names.take(count), choices.take(count).map(RatCharacter::valueOf), laps) }
    }
}

@Composable
private fun ColumnScope.GameHandoff(session: GameSession, onBegin: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Turno de ${session.currentPlayer.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Image(painterResource(session.currentPlayer.character.art().normalSprite), session.currentPlayer.character.label, Modifier.size(180.dp), contentScale = ContentScale.Fit)
        Text(session.currentPlayer.character.label, color = Color.White)
        Text("Pasa el teléfono a ${session.currentPlayer.name}", color = Color.White)
        GameArtButton("Comenzar turno", onClick = onBegin)
        TextButton(onClick = onHome) { Text("Inicio del juego", color = Color.White) }
    }
}

@Composable
private fun ColumnScope.GameResults(session: GameSession, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Clasificación final", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        session.ranking.forEachIndexed { index, id ->
            val player = session.players.first { it.id == id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(player.character.art().normalSprite), player.character.label, Modifier.size(72.dp), contentScale = ContentScale.Fit)
                Text("${index + 1}.º ${player.name}", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        GameArtButton("Inicio del juego", onClick = onHome)
    }
}
