package com.r0ybt.arachn0de.game

/** Pure transitions shared by human controls, bots and restoration. No wall-clock timers. */
object RaceRules {
    private fun update(s: RaceSession, id: Int, change: (RacePlayer)->RacePlayer) = s.copy(players = s.players.map { if(it.id==id) change(it) else it })
    private fun random(s: RaceSession, bound: Int): Pair<RaceSession,Int> {
        // Rejection sampling avoids modulo bias; same stream for every control type.
        var state = s.random; var bits: Long; var value: Long
        do { state = state * 6364136223846793005L + 1442695040888963407L; bits = state ushr 1; value = bits % bound } while(bits - value + (bound-1) < 0)
        return s.copy(random=state) to value.toInt()
    }
    fun newGame(characters: List<Racer>, humans: Set<Int>, laps: Int = 3, seed: Long = System.nanoTime()): RaceSession {
        require(characters.size in 2..10 && characters.distinct().size == characters.size && humans.all { it in characters.indices } && laps in 1..9)
        var s = RaceSession(RaceTracks.circuit(),characters.mapIndexed { i,c -> RacePlayer(i,c,i in humans) },laps=laps,random=seed)
        val boxes = s.track.boxes.toMutableSet()
        while(boxes.size<8) { val (next,n)=random(s,31); s=next; if(n+1 !in setOf(5,18)) boxes.add(n+1) }
        return s.copy(track=s.track.copy(boxes=boxes),message="Salida · $laps vueltas")
    }
    private fun hit(s: RaceSession, ids: Collection<Int>): RaceSession = s.copy(players=s.players.map { if(it.id in ids && !it.barrier) it.copy(stunned=true) else it })
    fun begin(s: RaceSession): RaceSession {
        require(s.phase==RacePhase.HANDOFF)
        var n = update(s,s.actor.id) { it.copy(turns=it.turns+1,barrier=false,invisible=false) }
            .copy(itemUsed=false,cost=0,secondChance=false,die=0,steps=0,prize=null,effect=null)
        val explosions=n.dynamites.filter { it.owner==n.actor.id && it.dueTurn<=n.actor.turns }
        explosions.forEach { d -> n=hit(n,n.players.filter { it.tile in d.zone }.map { it.id }).copy(effect=RaceItem.DYNAMITE,message="¡Explosión! Los impactos no acumulan turnos perdidos.",event=n.event+1) }
        n=n.copy(dynamites=n.dynamites-explosions.toSet())
        val eye = n.eyes.firstOrNull { it.owner==n.actor.id && it.dueTurn<=n.actor.turns }
        if(n.actor.stunned) return n.copy(phase=RacePhase.SKIPPED,eyes=n.eyes.filterNot { it.owner==n.actor.id },message="${n.actor.character.label}: aturdido · omite movimiento")
        if(eye!=null && n.players.any { it.id!=n.actor.id && it.tile in eye.zone }) return n.copy(phase=RacePhase.EYE,message="Ojo: dispara un rayo o termina la vigilancia")
        return n.copy(phase=RacePhase.READY,eyes=n.eyes.filterNot { it.owner==n.actor.id },message="${n.actor.character.label}: objeto opcional antes del dado")
    }
    fun eye(s: RaceSession, target: Int?): RaceSession {
        require(s.phase==RacePhase.EYE)
        val eye=s.eyes.single { it.owner==s.actor.id }
        require(target==null || s.players.any { it.id==target && it.id!=s.actor.id && it.tile in eye.zone })
        var n=s.copy(phase=RacePhase.READY,eyes=s.eyes.filterNot { it.owner==s.actor.id },itemUsed=target!=null,cost=0,
            message=if(target==null) "Vigilancia terminada" else "Rayo · sin coste adicional",effect=if(target!=null)RaceItem.EYE else null,event=s.event+1)
        if(target!=null) n=hit(n,listOf(target))
        return n
    }
    fun use(s: RaceSession, slot: Int, target: Int? = null, cells: List<Int> = emptyList(), choices: Map<Int,Int> = emptyMap()): RaceSession {
        require(s.phase==RacePhase.READY && !s.itemUsed && !s.actor.stunned)
        val held=s.actor.inventory.getOrNull(slot) ?: error("Objeto inexistente")
        require(held.readyTurn<=s.actor.turns)
        val p=s.actor; val b=s.balance
        choices.forEach { (from,to) -> require(to in s.track.tile(from).next || from in s.track.tile(to).next) }
        var n=s
        when(held.item) {
            RaceItem.ROCK -> {
                val path=s.track.path(p.tile,b.rock,choices)
                val first=path.firstOrNull { cell -> s.players.any { it.id!=p.id && it.tile==cell } }
                require(first!=null) { "Sin impacto válido; conservas la roca" }
                val rivals=s.players.filter { it.id!=p.id && it.tile==first }.sortedBy { it.id }
                n=hit(s,listOf(rivals[s.rockHits % rivals.size].id)).copy(rockHits=s.rockHits+1)
            }
            RaceItem.WASPS -> {
                require(s.visible(p.id).any { it.id==target && it.id!=p.id && !it.invisible && it.tile in s.track.reachable(p.tile,b.wasps) })
                n=hit(s,listOf(target!!))
            }
            RaceItem.REROLL -> n=s.copy(secondChance=true)
            RaceItem.CALTROPS -> {
                val valid=s.track.reachable(p.tile,b.placement)
                require(cells.distinct().size==cells.size && cells.size==minOf(2,valid.size) && cells.all { it in valid })
                n=s.copy(traps=s.traps+cells.map { RaceTrap(p.id,it,s.round+b.trapRounds) })
            }
            RaceItem.DYNAMITE, RaceItem.EYE -> {
                val valid=if(held.item==RaceItem.EYE) s.track.reachable(p.tile,b.eye)+s.track.reachable(p.tile,b.eye,true) else s.track.reachable(p.tile,b.placement)
                require(cells.size==1 && cells.single() in valid)
                val center=cells.single(); val radius=if(held.item==RaceItem.EYE)b.eyeRadius else b.blastRadius
                val prepared=RacePrepared(p.id,center,s.track.zone(center,radius,choices),p.turns+1)
                n=if(held.item==RaceItem.EYE) s.copy(eyes=s.eyes.filterNot { it.owner==p.id }+prepared) else s.copy(dynamites=s.dynamites+prepared)
            }
            RaceItem.BARRIER -> n=update(s,p.id) { it.copy(barrier=true) }
            RaceItem.CLOAK -> n=update(s,p.id) { it.copy(invisible=true) }
        }
        return update(n,p.id) { it.copy(inventory=it.inventory.filterIndexed { index,_ -> index!=slot }) }
            .copy(itemUsed=true,cost=1,effect=held.item,event=s.event+1,message="${held.item.label} · −1 casilla este turno")
    }
    fun roll(s: RaceSession): RaceSession {
        require(s.phase==RacePhase.READY && !s.actor.stunned)
        val (n,value)=random(s,6)
        return if(s.secondChance) n.copy(die=value+1,phase=RacePhase.REROLL,message="Dado ${value+1}: conserva o repite una vez") else movement(n,value+1)
    }
    fun reroll(s: RaceSession, repeat: Boolean): RaceSession {
        require(s.phase==RacePhase.REROLL)
        if(!repeat) return movement(s.copy(secondChance=false),s.die)
        val(n,value)=random(s,6); return movement(n.copy(secondChance=false),value+1)
    }
    private fun movement(s: RaceSession, die: Int): RaceSession {
        val steps=(die-s.cost).coerceAtLeast(0)
        val n=s.copy(die=die,steps=steps,phase=RacePhase.MOVING,message="Dado $die${if(s.cost==1) " − 1" else ""} = $steps pasos",effect=null)
        return if(steps==0) land(n) else n
    }
    fun step(s: RaceSession, branch: Int? = null): RaceSession {
        require(s.phase==RacePhase.MOVING || s.phase==RacePhase.BRANCH)
        require(s.steps>0)
        val links=s.track.tile(s.actor.tile).next
        if(links.size>1 && branch==null) return s.copy(phase=RacePhase.BRANCH,message="Elige: Directa o +2 casillas · caja")
        val to=branch ?: links.single(); require(to in links)
        val newLap=s.actor.lap+if(to==0)1 else 0
        var n=update(s,s.actor.id) { it.copy(tile=to,lap=newLap,collected=if(to==0) emptySet() else it.collected,history=(it.history+to).takeLast(16)) }
            .copy(steps=s.steps-1,phase=RacePhase.MOVING)
        if(newLap>=s.laps) return n.copy(phase=RacePhase.WON,winner=s.actor.id,message="¡${s.actor.character.label} gana la carrera!")
        if(to in n.track.boxes && to !in n.actor.collected) {
            n=update(n,n.actor.id) { it.copy(collected=it.collected+to) }
            val(next,value)=random(n,RaceItem.entries.size)
            return next.copy(phase=RacePhase.PRIZE,prize=RacePrize(RaceItem.entries[value]),message="Caja sorpresa",effect=null)
        }
        return if(n.steps==0) land(n) else n
    }
    /** null discards; 0/1 replaces a full slot. Pending movement resumes without replaying pickup. */
    fun prize(s: RaceSession, replace: Int? = null): RaceSession {
        require(s.phase==RacePhase.PRIZE && s.prize!=null)
        val p=s.actor; val held=HeldRaceItem(s.prize.item,p.turns+1)
        require(replace==null || replace in p.inventory.indices)
        val inventory=if(p.inventory.size<2) p.inventory+held else if(replace==null)p.inventory else p.inventory.mapIndexed { i,item -> if(i==replace)held else item }
        val n=update(s,p.id) { it.copy(inventory=inventory) }.copy(prize=null,phase=RacePhase.MOVING,message="${s.prize.item.label} · disponible desde tu siguiente turno")
        return if(n.steps==0) land(n) else n
    }
    private fun land(s: RaceSession): RaceSession {
        val triggered=s.traps.filter { it.tile==s.actor.tile && it.owner!=s.actor.id }
        val n=if(triggered.isEmpty())s else hit(s,listOf(s.actor.id)).copy(traps=s.traps-triggered.toSet(),effect=RaceItem.CALTROPS,event=s.event+1,message="Trampa al caer · próximo movimiento omitido")
        return n.copy(phase=RacePhase.END)
    }
    fun end(s: RaceSession): RaceSession {
        require(s.phase==RacePhase.END || s.phase==RacePhase.SKIPPED)
        val n=if(s.phase==RacePhase.SKIPPED)update(s,s.actor.id) { it.copy(stunned=false) } else s
        val next=(s.current+1)%s.players.size; val round=s.round+if(next==0)1 else 0
        return n.copy(current=next,round=round,phase=RacePhase.HANDOFF,traps=n.traps.filter { it.expiresRound>round },prize=null,steps=0)
    }
}

/** Choices depend on public/detected positions only. Random prizes and dice stay in RaceRules. */
object RaceBots {
    fun act(s: RaceSession): RaceSession = when(s.phase) {
        RacePhase.HANDOFF -> RaceRules.begin(s)
        RacePhase.EYE -> RaceRules.eye(s,s.visible(s.actor.id).firstOrNull { it.id!=s.actor.id && it.tile in s.eyes.single { eye -> eye.owner==s.actor.id }.zone }?.id)
        RacePhase.READY -> {
            val slot=s.actor.inventory.indexOfFirst { it.readyTurn<=s.actor.turns }
            if(slot<0 || s.itemUsed) RaceRules.roll(s) else {
                val visible=s.visible(s.actor.id).filter { it.id!=s.actor.id }
                val item=s.actor.inventory[slot].item
                val cells=s.track.reachable(s.actor.tile,s.balance.placement).toList()
                val target=visible.firstOrNull { it.tile in s.track.reachable(s.actor.tile,s.balance.wasps) }?.id
                val chosen=when(item) { RaceItem.CALTROPS -> cells.take(2); RaceItem.DYNAMITE -> cells.takeLast(1); RaceItem.EYE -> s.track.reachable(s.actor.tile,s.balance.eye).toList().takeLast(1); else -> emptyList() }
                // Do not probe hidden opponents with invalid rock attempts.
                val rockVisible=visible.any { it.tile in s.track.path(s.actor.tile,s.balance.rock,emptyMap()) }
                if(item==RaceItem.ROCK && !rockVisible || item==RaceItem.WASPS && target==null) RaceRules.roll(s)
                else runCatching { RaceRules.use(s,slot,target,chosen) }.getOrElse { RaceRules.roll(s) }
            }
        }
        RacePhase.REROLL -> RaceRules.reroll(s,s.die<=3)
        RacePhase.BRANCH -> { val links=s.track.tile(s.actor.tile).next; RaceRules.step(s,if(s.actor.inventory.size<2)links.last() else links.first()) }
        RacePhase.MOVING -> RaceRules.step(s)
        RacePhase.PRIZE -> RaceRules.prize(s,if(s.actor.inventory.size==2)0 else null)
        RacePhase.END, RacePhase.SKIPPED -> RaceRules.end(s)
        RacePhase.WON -> s
    }
}
