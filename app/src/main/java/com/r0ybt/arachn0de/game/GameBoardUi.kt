package com.r0ybt.arachn0de.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
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
    onAbility: () -> Unit, onTile: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
    viewerId: String = session.currentPlayer.id, active: Boolean = true, onArrived: () -> Unit = {},
    onEye: () -> Unit = {}, onRoute: (String) -> Unit = {}, onCombat: () -> Unit = {},
    onContinue: () -> Unit = {}, countdown: Int = 10, animationRevision: Long = 0) {
    var help by remember { mutableStateOf(false) }
    val current = session.currentPlayer
    val viewer = session.players.first { it.id == viewerId }
    val human = current.control == PlayerControl.HUMAN
    val textures = gameTextures()
    val motion = session.motion
    val progress = remember(motion) { androidx.compose.animation.core.Animatable(if (motion == null) 1f else 0f) }
    LaunchedEffect(motion, active, animationRevision) {
        if (motion != null && active) { progress.snapTo(0f); progress.animateTo(1f, tween(GameExpansion.STEP_MILLIS)); onArrived() }
    }
    val visible = remember(session, map, viewerId) { GameVision.visibleTiles(session, map, viewerId) }
    val fog = remember(session, map, viewerId) { GameFog.cells(session, map, viewerId) }
    val players = GameVision.visiblePlayers(session, map, viewerId)
    val objects = GameVision.visibleObjects(session, map, viewerId)
    val legal = if (human && session.phase == TurnPhase.PLACING) GameRules.legalPlacements(session, map) else emptySet()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(viewer.character.art().normalSprite), viewer.character.label, Modifier.size(42.dp), contentScale = ContentScale.Fit)
        Text(if (human) "${current.name} · ${current.character.label}\nVuelta ${current.completedLaps + 1}/${session.targetLaps}" else "Bot jugando · visión de ${viewer.name}", style = MaterialTheme.typography.titleSmall)
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val boardWidth = minOf(maxWidth, 480.dp)
        val cell = boardWidth / map.columns
        val density = LocalDensity.current
        val slot = map.slots.first { it.tileId == viewer.tileId }
        LaunchedEffect(viewer.id, viewer.tileId, cell) {
            val target = with(density) { (cell * (slot.row - 2).coerceAtLeast(0)).roundToPx() }
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(boardWidth).height(cell * map.rows).testTag("game-board")) {
                Image(painterResource(R.drawable.fondo_cesped), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                map.slots.filter { it.type == SlotType.FOREST }.forEach { s ->
                    Image(painterResource(R.drawable.bosque), null, Modifier.offset(cell * s.column, cell * s.row).size(cell), contentScale = ContentScale.Fit)
                }
                map.slots.filter { it.type == SlotType.CAVE_WALL }.forEach { wall ->
                    Box(Modifier.offset(cell * wall.column, cell * wall.row).size(cell).graphicsLayer { rotationZ = wall.rotation.toFloat() }) {
                        val image = textures["paredcueva.png"]
                        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Box(Modifier.fillMaxWidth().height(cell * .25f).align(Alignment.Center).background(Color(0xFF69645D)))
                    }
                }
                map.slots.filter { it.type == SlotType.TILE }.forEach { s ->
                    Box(Modifier.offset(cell * s.column, cell * s.row).size(cell).testTag("game-${s.tileId}")) {
                        val terrain = map.tiles.getValue(s.tileId!!).terrain
                        val file = when (terrain) { Terrain.SWAMP -> "pantano.png"; Terrain.DRY_GRASS -> "pastoseco.png"; Terrain.CAVE -> "terrenocueva.png"; else -> null }
                        val image = textures[file]
                        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else { Image(painterResource(R.drawable.loseta), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            if (terrain != Terrain.MEADOW) Text(when (terrain) { Terrain.SWAMP -> "Pantano"; Terrain.CAVE -> "Cueva"; else -> "Pasto seco" },
                                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f)), color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        if (map.tiles.getValue(s.tileId).next.size > 1) Text("⑂", Modifier.align(Alignment.TopEnd), color = Color.White)
                        if (s.tileId == map.start || s.tileId == map.goal) Text(if (s.tileId == map.start) "Salida" else "Meta",
                            Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f)), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val side = size.width / map.columns
                    val positions = map.slots.filter { it.tileId != null }.associateBy { it.tileId!! }
                    map.tiles.values.forEach { tile -> tile.next.forEach { next ->
                        val from = positions.getValue(tile.id); val to = positions.getValue(next)
                        val a = androidx.compose.ui.geometry.Offset((from.column + .5f) * side, (from.row + .5f) * side)
                        val b = androidx.compose.ui.geometry.Offset((to.column + .5f) * side, (to.row + .5f) * side)
                        drawLine(Color.White.copy(alpha = .42f), a, b, 2f)
                        val tip = a + (b - a) * .75f; val direction = (b - a) / side
                        val normal = androidx.compose.ui.geometry.Offset(-direction.y, direction.x)
                        drawLine(Color.White.copy(alpha = .6f), tip, tip - direction * 6f + normal * 4f, 2f)
                        drawLine(Color.White.copy(alpha = .6f), tip, tip - direction * 6f - normal * 4f, 2f)
                    } }
                }
                Canvas(Modifier.matchParentSize().testTag("world-fog")) {
                    val side = size.width / map.columns
                    map.slots.forEach { slot ->
                        val level = fog.getValue(slot.row to slot.column)
                        if (level != FogLevel.VISIBLE) drawRect(Color(0xFF080C09).copy(alpha = if (level == FogLevel.EXPLORED) .58f else .88f),
                            androidx.compose.ui.geometry.Offset(slot.column * side, slot.row * side), androidx.compose.ui.geometry.Size(side, side))
                    }
                }
                session.spider?.takeIf { !it.defeated && it.tileId in visible }?.let { spider ->
                    val position = map.slots.first { it.tileId == spider.tileId }
                    Box(Modifier.offset(cell * position.column, cell * position.row).size(cell).testTag("game-spider")) {
                        val image = textures["araña.png"]
                        if (image != null) Image(image, "Araña gigante", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text("Araña", Modifier.align(Alignment.Center).background(Color(0xFF542825)), color = Color.White)
                    }
                }
                objects.forEach { obj ->
                    val s = map.slots.first { it.tileId == obj.tileId }
                    Image(painterResource(obj.type.drawable()), GameDefinitions.objects.getValue(obj.type).label,
                        Modifier.offset(cell * s.column + cell * .1f, cell * s.row + cell * .1f).size(cell * .8f).testTag("object-${obj.id}"), contentScale = ContentScale.Fit)
                }
                players.forEach { player ->
                    // A logically visible destination must not expose an animation
                    // that still originates outside this viewer's field of vision.
                    if (player.id != viewerId && motion?.playerId == player.id && motion.from !in visible) return@forEach
                    val s = map.slots.first { it.tileId == player.tileId }
                    val shape = RoundedCornerShape(4.dp)
                    val origin = motion?.takeIf { it.playerId == player.id }?.let { move -> map.slots.first { it.tileId == move.from } }
                    val visualColumn = origin?.let { it.column + (s.column - it.column) * progress.value } ?: s.column.toFloat()
                    val visualRow = origin?.let { it.row + (s.row - it.row) * progress.value } ?: s.row.toFloat()
                    Column(Modifier.offset(cell * visualColumn + cell / 2 * player.quadrant.column + cell * .035f,
                        cell * visualRow + cell / 2 * player.quadrant.row + cell * .015f).width(cell * .43f)) {
                        Image(painterResource(player.character.art().token), "${player.name}, ${player.character.label}, vida ${player.health}/100${if (player.exhausted) ", agotado" else ""}${if (player.trapped) ", atrapado" else ""}",
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
    if (!human && session.phase != TurnPhase.RESULT) ParchmentPanel(Modifier.fillMaxWidth()) { Text("Bot jugando…", color = ParchmentInk) }
    else if (session.phase == TurnPhase.CHOOSING_ROUTE) ParchmentPanel(Modifier.fillMaxWidth()) {
        Text("Elige el camino", color = ParchmentInk)
        map.tiles.getValue(current.tileId).next.forEachIndexed { i, id ->
            val terrain = map.tiles.getValue(id).terrain
            GameArtButton(if (terrain == Terrain.DRY_GRASS || terrain == Terrain.CAVE) "Atajo ${i + 1}" else "Camino principal", modifier = Modifier.testTag("route-$id")) { onRoute(id) }
        }
    } else if (session.phase == TurnPhase.COMBAT) ParchmentPanel(Modifier.fillMaxWidth()) {
        Text("Encuentro con la araña", color = ParchmentInk)
        GameArtButton("Combatir", onClick = onCombat)
    } else if (session.phase == TurnPhase.RESULT) ParchmentPanel(Modifier.fillMaxWidth()) {
        if (human || current.tileId in visible && (session.combat == null || session.spider?.tileId in visible)) {
            HealthBar(current.health)
            Text(session.result ?: "Turno terminado", color = ParchmentInk)
            session.combat?.let { combat -> Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(diceDrawable(combat.playerDie)), "Dado del jugador: ${combat.playerDie}", Modifier.size(40.dp))
                Text("${combat.playerDie} · ${combat.spiderDie}", color = ParchmentInk)
                Image(painterResource(diceDrawable(combat.spiderDie)), "Dado de la araña: ${combat.spiderDie}", Modifier.size(40.dp))
            } }
        } else Text("Turno del bot terminado.", color = ParchmentInk)
        GameArtButton(if (session.resultDeadline == null) "Presentando resultado…" else "Continuar ($countdown)", enabled = session.resultDeadline != null,
            modifier = Modifier.testTag("continue-game-turn"), onClick = onContinue)
    } else if (session.phase == TurnPhase.PLACING) ParchmentPanel(Modifier.fillMaxWidth()) {
        Text(if (session.selectedPlacementTile == null) "Elige una casilla resaltada" else "Confirmar colocación", color = ParchmentInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameArtButton("Colocar", session.selectedPlacementTile != null, Modifier.weight(1f), onConfirm)
            GameArtButton("Cancelar", modifier = Modifier.weight(1f), onClick = onCancel)
        }
    } else ParchmentPanel(Modifier.fillMaxWidth()) {
        HealthBar(current.health)
        if (current.trapped) Text("Atrapado: 4–6 para escapar; escapar consume el turno.", color = ParchmentInk)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Visibility, contentDescription = null)
            GameArtButton("Ojo de exploración ×${current.eyeUses}", enabled = session.phase == TurnPhase.READY && current.eyeUses > 0 && current.eyeTurns == 0,
                modifier = Modifier.testTag("exploration-eye"), onClick = onEye)
        }
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
internal fun DamageOverlay(event: PendingDamage, attacker: GamePlayer, attackerVisible: Boolean = true, onContinue: () -> Unit) {
    var target by remember(event.id) { mutableFloatStateOf(event.hpBefore.toFloat()) }
    var finished by remember(event.id) { mutableStateOf(false) }
    val hp by animateFloatAsState(target, tween(900), label = "damage-hp")
    LaunchedEffect(event.id) { delay(80); target = event.hpAfter.toFloat(); delay(1000); finished = true }
    GameOverlay {
        if (attackerVisible) Image(battlePainter(attacker.character.art().battleSprite), attacker.character.label, Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
            .fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
        ParchmentPanel(Modifier.fillMaxWidth()) {
            Text(if (attackerVisible) "${attacker.name} te atacó" else "Recibiste daño", color = ParchmentInk, style = MaterialTheme.typography.titleLarge)
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
