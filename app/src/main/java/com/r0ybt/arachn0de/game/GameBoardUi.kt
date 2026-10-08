package com.r0ybt.arachn0de.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R
import kotlinx.coroutines.delay

@Composable
internal fun ColumnScope.GameBoard(session: GameSession, map: GameMap, onRoll: () -> Unit,
    onAbility: () -> Unit, onTile: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var help by remember { mutableStateOf(false) }
    val current = session.currentPlayer
    val visible = remember(session, map) { GameVision.visibleTiles(session, map, current.id) }
    val players = GameVision.visiblePlayers(session, map, current.id)
    val objects = GameVision.visibleObjects(session, map, current.id)
    val legal = if (session.phase == TurnPhase.PLACING) GameRules.legalPlacements(session, map) else emptySet()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(current.character.art().normalSprite), current.character.label, Modifier.size(42.dp), contentScale = ContentScale.Fit)
        Text("${current.name} · ${current.character.label}\nVuelta ${current.completedLaps + 1}/${session.targetLaps}", style = MaterialTheme.typography.titleSmall)
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val boardWidth = minOf(maxWidth, 480.dp)
        val cell = boardWidth / map.columns
        val density = LocalDensity.current
        val slot = map.slots.first { it.tileId == current.tileId }
        LaunchedEffect(current.id, current.tileId, cell) {
            val target = with(density) { (cell * (slot.row - 2).coerceAtLeast(0)).roundToPx() }
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(boardWidth).height(cell * map.rows).testTag("game-board")) {
                Image(painterResource(R.drawable.fondo_cesped), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                map.slots.filter { it.type == SlotType.FOREST }.forEach { s ->
                    Image(painterResource(R.drawable.bosque), null, Modifier.offset(cell * s.column, cell * s.row).size(cell), contentScale = ContentScale.Fit)
                }
                map.slots.filter { it.type == SlotType.TILE }.forEach { s ->
                    Box(Modifier.offset(cell * s.column, cell * s.row).size(cell).testTag("game-${s.tileId}")) {
                        Image(painterResource(R.drawable.loseta), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        if (s.tileId == map.start || s.tileId == map.goal) Text(if (s.tileId == map.start) "Salida" else "Meta",
                            Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f)), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Canvas(Modifier.matchParentSize().testTag("world-fog")) {
                    val side = size.width / map.columns
                    val mask = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        map.slots.filter { it.tileId in visible }.forEach { slot ->
                            addRect(Rect(slot.column * side, slot.row * side,
                                (slot.column + 1) * side, (slot.row + 1) * side))
                        }
                    }
                    drawPath(mask, Color(0xFF080C09))
                }
                objects.forEach { obj ->
                    val s = map.slots.first { it.tileId == obj.tileId }
                    Image(painterResource(obj.type.drawable()), GameDefinitions.objects.getValue(obj.type).label,
                        Modifier.offset(cell * s.column + cell * .1f, cell * s.row + cell * .1f).size(cell * .8f).testTag("object-${obj.id}"), contentScale = ContentScale.Fit)
                }
                players.forEach { player ->
                    val s = map.slots.first { it.tileId == player.tileId }
                    val shape = RoundedCornerShape(4.dp)
                    Column(Modifier.offset(cell * s.column + cell / 2 * player.quadrant.column + cell * .035f,
                        cell * s.row + cell / 2 * player.quadrant.row + cell * .015f).width(cell * .43f)) {
                        Image(painterResource(player.character.art().token), "${player.name}, ${player.character.label}, vida ${player.health}/100${if (player.exhausted) ", agotado" else ""}",
                            Modifier.size(cell * .43f).clip(shape).background(Color(0xFFEAD9B8))
                                .border(1.dp, if (player.exhausted) Color.Red else Color(0xFF665039), shape)
                                .padding(2.dp).testTag("token-${player.id}-${player.quadrant.name}"), contentScale = ContentScale.Fit,
                            colorFilter = if (player.exhausted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
                        Spacer(Modifier.height(cell * .01f))
                        Box(Modifier.fillMaxWidth().height(cell * .035f).clip(shape).background(Color(0xFF513C33))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((player.health / 100f).coerceIn(0f, 1f)).background(Color(0xFFC52727)))
                        }
                    }
                }
                // Only legal visible tiles get interactive highlights. No hidden dynamics have semantics nodes.
                map.slots.filter { it.tileId in legal }.forEach { s ->
                    Box(Modifier.offset(cell * s.column, cell * s.row).size(cell)
                        .border(3.dp, if (session.selectedPlacementTile == s.tileId) Color.White else Color(0xFFFFD55A))
                        .clickable { onTile(requireNotNull(s.tileId)) }.testTag("place-${s.tileId}"))
                }
            }
        }
    }
    if (session.phase == TurnPhase.PLACING) ParchmentPanel(Modifier.fillMaxWidth()) {
        Text(if (session.selectedPlacementTile == null) "Elige una casilla resaltada" else "Confirmar colocación", color = ParchmentInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameArtButton("Colocar", session.selectedPlacementTile != null, Modifier.weight(1f), onConfirm)
            GameArtButton("Cancelar", modifier = Modifier.weight(1f), onClick = onCancel)
        }
    } else ParchmentPanel(Modifier.fillMaxWidth()) {
        HealthBar(current.health)
        val ability = GameDefinitions.characters.getValue(current.character).ability
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clickable(enabled = session.phase == TurnPhase.READY && current.abilityCharges > 0,
                role = androidx.compose.ui.semantics.Role.Button, onClick = onAbility).testTag("ability-button"), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(ability.drawable()), null, Modifier.size(44.dp), contentScale = ContentScale.Fit,
                    alpha = if (current.abilityCharges > 0) 1f else .4f)
                Text("${GameDefinitions.objects.getValue(ability).label} ×${current.abilityCharges}", color = ParchmentInk, style = MaterialTheme.typography.labelMedium)
            }
            Text("?", Modifier.clip(RoundedCornerShape(8.dp)).clickable { help = true }
                .padding(12.dp).testTag("ability-help"), color = ParchmentInk)
            Column(Modifier.weight(1f).clickable(enabled = session.phase == TurnPhase.READY,
                role = androidx.compose.ui.semantics.Role.Button, onClick = onRoll).testTag("dice-button"), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(diceDrawable(if (session.lastPlayerId == current.id) session.lastRoll ?: 1 else 1)), null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                Text(if (session.phase == TurnPhase.MOVING) "Avanzando…" else "Tirar dado", color = ParchmentInk, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (help) androidx.compose.ui.window.Dialog(onDismissRequest = { help = false }) {
        val ability = GameDefinitions.characters.getValue(current.character).ability
        ParchmentPanel {
            Text(GameDefinitions.objects.getValue(ability).label, color = ParchmentInk, style = MaterialTheme.typography.titleMedium)
            Text(ability.description(), color = ParchmentInk)
            GameArtButton("Cerrar", onClick = { help = false })
        }
    }

}

@Composable
internal fun HealthBar(health: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(health.toFloat(), tween(750), label = "hp")
    HealthBarValue(animated, health, modifier)
}
@Composable
private fun HealthBarValue(value: Float, label: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Vida $label/100", color = ParchmentInk, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color(0xFF705A45))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((value / 100f).coerceIn(0f, 1f)).background(Color(0xFFC52727)))
        }
    }
}

@Composable
private fun GameOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
internal fun DiceOverlay(result: Int, onFinished: () -> Unit) {
    var face by remember(result) { mutableIntStateOf(1) }
    var frame by remember(result) { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(if (frame == DiceAnimation.delays.lastIndex) 0f else if (frame % 2 == 0) -12f else 12f, tween(100), label = "dice-rotation")
    LaunchedEffect(result) {
        DiceAnimation.faces(result).forEachIndexed { index, value ->
            frame = index; face = value; delay(DiceAnimation.delays[index])
        }
        delay(300); onFinished()
    }
    GameOverlay {
        Image(painterResource(diceDrawable(face)), "Dado: $face", Modifier.sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
            .fillMaxWidth().aspectRatio(1f).graphicsLayer { rotationZ = rotation }.testTag("rolling-dice"), contentScale = ContentScale.Fit)
        Text("${if (frame == DiceAnimation.delays.lastIndex) "Resultado: $result" else "Rodando…"}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
internal fun DamageOverlay(event: PendingDamage, attacker: GamePlayer, onContinue: () -> Unit) {
    var target by remember(event.id) { mutableFloatStateOf(event.hpBefore.toFloat()) }
    var finished by remember(event.id) { mutableStateOf(false) }
    val hp by animateFloatAsState(target, tween(900), label = "damage-hp")
    LaunchedEffect(event.id) { delay(80); target = event.hpAfter.toFloat(); delay(1000); finished = true }
    GameOverlay {
        Image(battlePainter(attacker.character.art().battleSprite), attacker.character.label, Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
            .fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
        ParchmentPanel(Modifier.fillMaxWidth()) {
            Text("${attacker.name} te atacó", color = ParchmentInk, style = MaterialTheme.typography.titleLarge)
            Text("${event.source?.let { GameDefinitions.objects.getValue(it).label } ?: "Ataque"}: −${event.damage} HP", color = ParchmentInk)
            HealthBarValue(hp, hp.toInt().coerceIn(0, 100))
        }
        GameArtButton("Continuar", finished, onClick = onContinue)
    }
}

@Composable
internal fun GameRestOverlay(player: GamePlayer, onRest: () -> Unit) {
    GameOverlay {
        Image(painterResource(player.character.art().normalSprite), player.character.label, Modifier.size(180.dp), contentScale = ContentScale.Fit)
        ParchmentPanel {
            Text(if (player.exhausted) "${player.name} está agotado" else "${player.name} pierde este turno", color = ParchmentInk, style = MaterialTheme.typography.titleLarge)
            Text(if (player.exhausted) "Descansa este turno y recupera 50 HP." else "La trampa impide jugar este turno.", color = ParchmentInk)
        }
        GameArtButton("Descansar y pasar turno", onClick = onRest)
    }
}
