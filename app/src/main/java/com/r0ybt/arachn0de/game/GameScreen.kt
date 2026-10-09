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
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val ParchmentInk = Color(0xFF34200E)

@Composable
internal fun GameScreen(onBack: () -> Unit, clock: () -> Long = { System.currentTimeMillis() }) {
    val context = LocalContext.current.applicationContext
    val database = (context as com.r0ybt.arachn0de.Arachn0deApplication).database
    val repository = remember(database) { GameStateRepository(database) }
    val scope = rememberCoroutineScope()
    val saved by remember(repository) { repository.observe() }.collectAsState(initial = null)
    var page by rememberSaveable { mutableStateOf("home") }
    var viewerId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(10) }
    val currentRevision = saved?.revision
    val session = saved?.session
    val map = session?.board ?: FirstGameMap.value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    fun act(action: (GameSession) -> GameSession) {
        val revision = currentRevision ?: return
        scope.launch { try { repository.act(revision, action) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { error = "No se pudo guardar la acción. La partida anterior se conserva; reintenta." } }
    }
    val viewer = session?.players?.firstOrNull { it.id == viewerId && it.control == PlayerControl.HUMAN }
        ?: session?.players?.firstOrNull { it.control == PlayerControl.HUMAN }
        ?: session?.players?.firstOrNull()
    LaunchedEffect(page, saved?.revision, resumed) {
        val game = session ?: return@LaunchedEffect
        if (page != "board" || !resumed) return@LaunchedEffect
        when (game.phase) {
            TurnPhase.MOVING -> { delay(60); act { GameExpansion.planStep(it, map) } }
            TurnPhase.RESOLVING -> { withFrameNanos { }; withFrameNanos { }; act { GameExpansion.resolveLanding(it, map) } }
            TurnPhase.RESULT -> {
                if (game.resultDeadline == null) {
                    delay(if (game.combat != null) 1800 else 900)
                    act { GameExpansion.presentResult(it, clock()) }
                } else {
                    while (true) {
                        val left = (game.resultDeadline - clock()).coerceAtLeast(0)
                        countdown = ((left + 999) / 1000).toInt()
                        if (left == 0L) { act { GameExpansion.continueTurn(it, map) }; break }
                        delay(minOf(left, 200))
                    }
                }
            }
            else -> Unit
        }
    }
    LaunchedEffect(page, saved?.revision, resumed, session?.currentPlayer?.control) {
        val game = session ?: return@LaunchedEffect
        if (page != "board" || !resumed || game.currentPlayer.control == PlayerControl.HUMAN) return@LaunchedEffect
        delay(550)
        val view = GameBots.observe(game, map)
        when (game.phase) {
            TurnPhase.HANDOFF -> act(GameRules::beginTurn)
            TurnPhase.DAMAGE -> act(GameRules::acknowledgeDamage)
            TurnPhase.REST -> act(GameExpansion::rest)
            TurnPhase.READY -> {
                val placement = GameBots.placement(view)
                when {
                    GameBots.useEye(view) -> act { GameExpansion.explore(it, map) }
                    placement != null -> act(GameRules::startPlacement)
                    else -> act { GameRules.roll(it, GameRules.rollD6()) }
                }
            }
            TurnPhase.PLACING -> {
                val place = GameBots.placement(view) ?: view.legalPlacements.firstOrNull()
                if (game.selectedPlacementTile != null) act { GameRules.confirmPlacement(it, map) }
                else if (place != null) act { GameRules.selectPlacement(it, map, place) } else act(GameRules::cancelPlacement)
            }
            TurnPhase.CHOOSING_ROUTE -> { val choice = GameBots.route(view, map.tiles.getValue(game.currentPlayer.tileId).next); act { GameExpansion.planStep(it, map, choice) } }
            TurnPhase.COMBAT -> act { GameExpansion.combat(it, map, GameRules.rollD6(), GameRules.rollD6()) }
            else -> Unit
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
                    page == "setup" -> GameSetup(starting) { names, characters, laps, controls ->
                        if (!starting) { starting = true; scope.launch {
                            try { repository.start(GameRules.newGame(names, characters, GameMapGenerator.generate(kotlin.random.Random.nextLong()), laps, controls)); page = "board" }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { error = "No se pudo crear la partida. Reintenta." }
                            finally { starting = false }
                        } }
                    }
                    page == "board" && session != null -> when (session.phase) {
                        TurnPhase.HANDOFF -> GameHandoff(session, onBegin = { viewerId = session.currentPlayer.id; act(GameRules::beginTurn) }, onHome = { page = "home" })
                        TurnPhase.WON -> GameResults(session) { page = "home" }
                        else -> GameBoard(session, map, onRoll = { act { GameRules.roll(it, GameRules.rollD6()) } },
                            onAbility = { act(GameRules::startPlacement) }, onTile = { id -> act { GameRules.selectPlacement(it, map, id) } },
                            onConfirm = { act { GameRules.confirmPlacement(it, map) } }, onCancel = { act(GameRules::cancelPlacement) },
                            viewerId = viewer?.id ?: session.currentPlayer.id, active = resumed,
                            onArrived = { act { GameExpansion.arrive(it, map) } }, onEye = { act { GameExpansion.explore(it, map) } },
                            onRoute = { id -> act { GameExpansion.planStep(it, map, id) } },
                            onCombat = { act { GameExpansion.combat(it, map, GameRules.rollD6(), GameRules.rollD6()) } },
                            onContinue = { act { GameExpansion.continueTurn(it, map) } }, countdown = countdown, animationRevision = saved!!.revision)
                    }
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (saved == null) Text("Cargando partida…")
                        GameArtButton("Nueva partida", enabled = saved != null, onClick = { page = "setup" })
                        if (session != null) GameArtButton(if (session.phase == TurnPhase.WON) "Ver resultado" else "Continuar partida", onClick = { page = "board" })
                    }
                }
            }
            if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Partida conservada") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Entendido") } })
            if (page == "board" && session != null && resumed) {
                when (session.phase) {
                    TurnPhase.ROLLING -> if (session.currentPlayer.control == PlayerControl.HUMAN || session.currentPlayer.tileId in GameVision.visibleTiles(session, map, viewer?.id ?: session.currentPlayer.id))
                        key(saved!!.revision) { DiceOverlay(requireNotNull(session.lastRoll)) { act(GameExpansion::finishRoll) } }
                    else HiddenBotRoll(saved!!.revision) { act(GameExpansion::finishRoll) }
                    TurnPhase.DAMAGE -> if (session.currentPlayer.control == PlayerControl.HUMAN) GameRules.currentDamage(session)?.let { event ->
                        val attacker = session.players.first { it.id == event.attackerId }
                        key(event.id) { DamageOverlay(event, attacker, attackerVisible = attacker.id in GameVision.visiblePlayers(session, map, session.currentPlayer.id).map { it.id }) { act(GameRules::acknowledgeDamage) } }
                    }
                    TurnPhase.REST -> if (session.currentPlayer.control == PlayerControl.HUMAN) GameRestOverlay(session.currentPlayer) { act(GameExpansion::rest) }
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
private fun GameSetup(starting: Boolean = false, onStart: (List<String>, List<RatCharacter>, Int, List<PlayerControl>) -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var laps by rememberSaveable { mutableIntStateOf(1) }
    var names by rememberSaveable { mutableStateOf(listOf("Jugador 1", "Jugador 2", "Jugador 3", "Jugador 4")) }
    var controls by rememberSaveable { mutableStateOf(List(4) { "HUMAN" }) }
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
            PlayerControl.entries.chunked(2).forEach { options -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { control -> FilterChip(selected = controls[index] == control.name,
                    onClick = { controls = controls.mapIndexed { i, value -> if (i == index) control.name else value } },
                    label = { Text(control.label) }, modifier = Modifier.weight(1f).testTag("control-$index-${control.name}")) }
            } }
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
        GameArtButton("Comenzar partida", enabled = !starting && names.take(count).all { it.trim().isNotEmpty() } && choices.take(count).distinct().size == count,
            modifier = Modifier.fillMaxWidth()) { onStart(names.take(count), choices.take(count).map(RatCharacter::valueOf), laps, controls.take(count).map(PlayerControl::valueOf)) }
    }
}

@Composable
private fun ColumnScope.GameHandoff(session: GameSession, onBegin: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Turno de ${session.currentPlayer.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Image(painterResource(session.currentPlayer.character.art().normalSprite), session.currentPlayer.character.label, Modifier.size(180.dp), contentScale = ContentScale.Fit)
        Text(session.currentPlayer.character.label, color = Color.White)
        if (session.currentPlayer.control == PlayerControl.HUMAN) {
            Text("Pasa el teléfono a ${session.currentPlayer.name}", color = Color.White)
            GameArtButton("Comenzar turno", onClick = onBegin)
        } else Text("Bot preparando su turno…", color = Color.White)
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

@Composable
private fun HiddenBotRoll(revision: Long, onFinished: () -> Unit) {
    LaunchedEffect(revision) { delay(DiceAnimation.delays.sum() + 300); onFinished() }
}
