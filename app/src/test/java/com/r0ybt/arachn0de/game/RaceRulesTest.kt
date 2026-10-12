package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test

class RaceRulesTest {
    private fun initial(humans: Set<Int> = setOf(0)) = RaceRules.newGame(Racer.entries.toList(),humans,3,42)
    private fun ready(item: RaceItem? = null, tile: Int = 0): RaceSession {
        val s=RaceRules.begin(initial().copy(track=RaceTracks.circuit()))
        return s.copy(players=s.players.map { if(it.id==0)it.copy(tile=tile,history=listOf(tile),inventory=item?.let { listOf(HeldRaceItem(it,1)) }.orEmpty()) else it })
    }
    private fun position(s: RaceSession,id: Int,tile: Int,invisible: Boolean = false,barrier: Boolean = false)=s.copy(players=s.players.map { if(it.id==id)it.copy(tile=tile,history=listOf(tile),invisible=invisible,barrier=barrier) else it })
    private fun move(s: RaceSession,steps: Int)=s.copy(phase=RacePhase.MOVING,steps=steps)
    @Test fun tenDistinctCharactersShareStartWithoutAutomaticDamage() {
        val s=initial(); assertEquals(10,s.players.size); assertEquals(10,s.visible(0).size); assertEquals(setOf(0),s.players.map { it.tile }.toSet())
        var n=RaceRules.begin(s); n=RaceRules.step(move(n,1)); assertFalse(n.players.any { it.stunned }); assertEquals(10,s.players.map { it.character }.distinct().size)
    }
    @Test fun branchesUseRemainingStepsAndRejoinWithExactlyTwoAdditionalTiles() {
        val s=move(ready(tile=5),3); assertEquals(RacePhase.BRANCH,RaceRules.step(s).phase)
        var direct=RaceRules.step(s,6); repeat(2) { direct=RaceRules.step(direct) }; assertEquals(8,direct.actor.tile)
        var detour=RaceRules.step(s.copy(steps=5),32)
        repeat(4) { if(detour.phase==RacePhase.PRIZE)detour=RaceRules.prize(detour); detour=RaceRules.step(detour) }
        assertEquals(8,detour.actor.tile); assertEquals(0,detour.steps)
    }
    @Test fun classificationDoesNotRewardExtraDetourSteps() {
        val s=position(position(ready(),0,34),1,7)
        assertTrue(s.ranking().indexOfFirst { it.id==1 }<s.ranking().indexOfFirst { it.id==0 })
        val lap=s.copy(players=s.players.map { if(it.id==2)it.copy(lap=1) else it }); assertEquals(2,lap.ranking().first().id)
    }
    @Test fun rangesFollowGraphNotVisualDistanceAndDoNotSweepWholeCircuit() {
        val t=initial().track
        assertEquals(setOf(1,2,3,4,5),t.reachable(0,5)); assertFalse(31 in t.reachable(0,5))
        assertEquals(listOf(32,33,34,35,8),t.path(5,5,mapOf(5 to 32)))
        assertTrue(8 in t.reachable(5,5)); assertTrue(t.zone(0,3,emptyMap()).size<=7)
    }
    @Test fun pickupWhilePassingHasStablePrizeAndIsOncePerLapForEachRacer() {
        var s=move(ready(tile=32),3); val boxes=s.track.boxes
        s=RaceRules.step(s); assertEquals(RacePhase.PRIZE,s.phase); assertEquals(2,s.steps); assertEquals(33,s.actor.tile)
        val prize=s.prize!!.item; s=RaceRules.prize(s); assertEquals(prize,s.actor.inventory.single().item); assertEquals(2,s.actor.inventory.single().readyTurn)
        assertEquals(boxes,s.track.boxes); assertTrue(33 in s.actor.collected)
        s=RaceRules.step(move(position(s,0,32),1)); assertEquals(RacePhase.END,s.phase)
        val other=RaceRules.step(move(position(s.copy(current=1),1,32),1)); assertEquals(RacePhase.PRIZE,other.phase)
        val nextLap=RaceRules.step(move(position(s.copy(players=s.players.map { if(it.id==0)it.copy(collected=emptySet(),lap=1) else it }),0,32),1)); assertEquals(RacePhase.PRIZE,nextLap.phase)
    }
    @Test fun fullInventoryRequiresDiscardOrReplacementWithoutChangingOtherSlot() {
        val s=ready().copy(phase=RacePhase.PRIZE,prize=RacePrize(RaceItem.EYE),steps=2,players=ready().players.map { if(it.id==0)it.copy(inventory=listOf(HeldRaceItem(RaceItem.ROCK,1),HeldRaceItem(RaceItem.BARRIER,1))) else it })
        assertEquals(s.actor.inventory,RaceRules.prize(s).actor.inventory)
        val replaced=RaceRules.prize(s,1); assertEquals(RaceItem.ROCK,replaced.actor.inventory[0].item); assertEquals(HeldRaceItem(RaceItem.EYE,2),replaced.actor.inventory[1]); assertEquals(2,replaced.steps)
    }
    @Test fun itemBeforeRollCostsOneAndCannotBeUsedAgainOrAfterRoll() {
        val used=RaceRules.use(ready(RaceItem.BARRIER),0); assertEquals(1,used.cost); assertTrue(used.actor.barrier)
        assertTrue(runCatching { RaceRules.use(used,0) }.isFailure)
        val rolled=RaceRules.roll(used); assertEquals((rolled.die-1).coerceAtLeast(0),rolled.steps)
        assertTrue(runCatching { RaceRules.use(rolled,0) }.isFailure)
        val one=(1L..100).map { seed->RaceRules.roll(used.copy(random=seed)) }.first { it.die==1 }; assertEquals(0,one.steps); assertEquals(RacePhase.END,one.phase)
    }
    @Test fun invalidTargetDoesNotConsumeOrCostAndNewPrizeCannotBeUsedImmediately() {
        val s=ready(RaceItem.WASPS)
        assertTrue(runCatching { RaceRules.use(s,0,target=9) }.isFailure); assertEquals(1,s.actor.inventory.size); assertFalse(s.itemUsed)
        val fresh=s.copy(players=s.players.map { if(it.id==0)it.copy(inventory=listOf(HeldRaceItem(RaceItem.BARRIER,2))) else it })
        assertTrue(runCatching { RaceRules.use(fresh,0) }.isFailure)
    }
    @Test fun secondChanceCanKeepFirstOrAcceptOnlyOneRerollWithOneCost() {
        val s=RaceRules.roll(RaceRules.use(ready(RaceItem.REROLL),0)); assertEquals(RacePhase.REROLL,s.phase); assertTrue(s.actor.inventory.isEmpty())
        val kept=RaceRules.reroll(s,false); assertEquals(s.die,kept.die); assertEquals(s.die-1,kept.steps)
        val repeated=RaceRules.reroll(s,true); assertEquals(repeated.die-1,repeated.steps); assertEquals(1,repeated.cost)
        assertTrue(runCatching { RaceRules.reroll(repeated,true) }.isFailure)
    }
    @Test fun rockHitsFirstTileOneRivalAndRotatesSharedTargetsIncludingInvisible() {
        var s=position(position(position(ready(RaceItem.ROCK),1,2,true),2,2),3,4)
        val first=RaceRules.use(s,0); assertTrue(first.players[1].stunned); assertFalse(first.players[2].stunned); assertFalse(first.players[3].stunned)
        s=s.copy(event=1,rockHits=1); val second=RaceRules.use(s,0); assertTrue(second.players[2].stunned); assertFalse(second.players[1].stunned)
    }
    @Test fun rockFollowsSelectedBranchAndNoTargetDoesNotConsume() {
        val s=position(ready(RaceItem.ROCK,5),1,34,true)
        assertTrue(runCatching { RaceRules.use(s,0) }.isFailure)
        assertTrue(RaceRules.use(s,0,choices=mapOf(5 to 32)).players[1].stunned)
    }
    @Test fun waspsIgnoreInterveningRacersButRejectInvisibleAndOutOfRange() {
        val s=position(position(ready(RaceItem.WASPS),1,2),2,8)
        val n=RaceRules.use(s,0,target=2); assertTrue(n.players[2].stunned); assertFalse(n.players[1].stunned)
        assertTrue(runCatching { RaceRules.use(position(s,2,8,true),0,target=2) }.isFailure)
        assertTrue(runCatching { RaceRules.use(position(s,2,9),0,target=2) }.isFailure)
    }
    @Test fun trapsTriggerOnlyLandingOpponentOnceAndExpireAfterThreeRounds() {
        val placed=RaceRules.use(ready(RaceItem.CALTROPS),0,cells=listOf(1,2))
        val same=RaceRules.step(move(placed,1)); assertFalse(same.actor.stunned); assertEquals(2,same.traps.size)
        val other=position(placed.copy(current=1,phase=RacePhase.MOVING,steps=2),1,0)
        val passed=RaceRules.step(other); assertFalse(passed.actor.stunned); val landed=RaceRules.step(passed)
        assertTrue(landed.actor.stunned); assertEquals(listOf(1),landed.traps.map { it.tile }); assertFalse(landed.players[2].stunned)
        val expiry=RaceRules.end(placed.copy(current=9,round=3,phase=RacePhase.END)); assertEquals(4,expiry.round); assertTrue(expiry.traps.isEmpty())
    }
    @Test fun oneReachableCellAllowsSingleTrapButDuplicateCellsAreRejected() {
        val s=ready(RaceItem.CALTROPS).copy(balance=RaceBalance(placement=1))
        assertEquals(listOf(1),RaceRules.use(s,0,cells=listOf(1)).traps.map { it.tile })
        assertTrue(runCatching { RaceRules.use(ready(RaceItem.CALTROPS),0,cells=listOf(1,1)) }.isFailure)
    }
    @Test fun humanAndBotControlsReceiveIdenticalDiceAndPrizesFromSameSeed() {
        val human=ready(); val bot=human.copy(players=human.players.map { it.copy(human=false) })
        assertEquals(RaceRules.roll(human).die,RaceRules.roll(bot).die)
        val a=RaceRules.step(move(position(human,0,32),2)); val b=RaceRules.step(move(position(bot,0,32),2))
        assertEquals(a.prize,b.prize); assertEquals(a.random,b.random)
    }
    @Test fun dynamiteFixesZoneAndExplodesAtOwnersNextStartEvenStunned() {
        var s=RaceRules.use(ready(RaceItem.DYNAMITE),0,cells=listOf(3)); assertEquals(setOf(1,2,3,4,5),s.dynamites.single().zone)
        s=position(position(position(s,0,3),1,2),2,5,barrier=true)
        s=s.copy(phase=RacePhase.HANDOFF,players=s.players.map { if(it.id==0)it.copy(stunned=true) else it })
        val fired=RaceRules.begin(s); assertEquals(RacePhase.SKIPPED,fired.phase); assertTrue(fired.players[1].stunned); assertFalse(fired.players[2].stunned); assertTrue(fired.dynamites.isEmpty())
    }
    @Test fun barrierPreventsStunAndExpiresAtNextOwnerStart() {
        val s=RaceRules.use(ready(RaceItem.BARRIER),0)
        val attack=position(s.copy(current=1,itemUsed=false,cost=0,players=s.players.map { if(it.id==1)it.copy(turns=1,inventory=listOf(HeldRaceItem(RaceItem.WASPS,1))) else it }),0,2,barrier=true)
        assertFalse(RaceRules.use(attack,0,target=0).players[0].stunned)
        assertFalse(RaceRules.begin(s.copy(phase=RacePhase.HANDOFF)).actor.barrier)
    }
    @Test fun cloakOnlyHidesOtherViewersAndPrivateEyeDetectsWithinFixedZone() {
        var s=RaceRules.use(ready(RaceItem.CLOAK),0)
        assertTrue(s.visible(0).any { it.id==0 }); assertFalse(s.visible(1).any { it.id==0 })
        s=s.copy(eyes=listOf(RacePrepared(1,0,setOf(0,1,31),1)))
        assertTrue(s.visible(1).any { it.id==0 }); assertFalse(s.visible(2).any { it.id==0 })
        assertEquals(10,s.ranking().size); assertFalse(RaceRules.begin(s.copy(phase=RacePhase.HANDOFF)).actor.invisible)
    }
    @Test fun eyeRayConsumesNextTurnsItemSlotButDoesNotChargeMovementAgain() {
        var s=position(RaceRules.use(ready(RaceItem.EYE),0,cells=listOf(4)),1,5,true)
        s=RaceRules.begin(s.copy(phase=RacePhase.HANDOFF)); assertEquals(RacePhase.EYE,s.phase)
        val fired=RaceRules.eye(s,1); assertTrue(fired.itemUsed); assertEquals(0,fired.cost); assertTrue(fired.players[1].stunned); assertTrue(fired.eyes.isEmpty())
        val declined=RaceRules.eye(s,null); assertFalse(declined.itemUsed)
    }
    @Test fun eyeExpiresWithoutFiringWhenOwnerStunnedOrNoTarget() {
        val s=RaceRules.use(ready(RaceItem.EYE),0,cells=listOf(4))
        val empty=RaceRules.begin(s.copy(phase=RacePhase.HANDOFF)); assertEquals(RacePhase.READY,empty.phase); assertTrue(empty.eyes.isEmpty())
        val hit=position(s,1,5).copy(phase=RacePhase.HANDOFF,players=position(s,1,5).players.map { if(it.id==0)it.copy(stunned=true) else it })
        val expired=RaceRules.begin(hit); assertEquals(RacePhase.SKIPPED,expired.phase); assertTrue(expired.eyes.isEmpty())
    }
    @Test fun stunsDoNotStackAndSkippedTurnRecoversOnlyAtEnd() {
        var s=position(ready(RaceItem.WASPS),1,2)
        s=RaceRules.use(s,0,target=1)
        val second=position(s.copy(current=2,phase=RacePhase.READY,itemUsed=false,cost=0,players=s.players.map { if(it.id==2)it.copy(turns=1,inventory=listOf(HeldRaceItem(RaceItem.WASPS,1))) else it }),2,0)
        val hit=RaceRules.use(second,0,target=1); val skip=RaceRules.begin(hit.copy(current=1,phase=RacePhase.HANDOFF))
        assertEquals(RacePhase.SKIPPED,skip.phase); assertTrue(runCatching { RaceRules.roll(skip) }.isFailure); assertFalse(RaceRules.end(skip).players[1].stunned)
    }
    @Test fun tenBotsFinishThreeLapsWithoutHumanIntervention() {
        var s=initial(emptySet()); var transitions=0
        while(s.phase!=RacePhase.WON && transitions++<30000) s=RaceBots.act(s)
        assertEquals(RacePhase.WON,s.phase); assertNotNull(s.winner); assertEquals(3,s.players[s.winner!!].lap); assertTrue(transitions<30000)
    }
    @Test fun botsCannotUseDirectedAttacksAgainstHiddenRivals() {
        var s=ready(RaceItem.WASPS).copy(players=ready(RaceItem.WASPS).players.map { it.copy(human=false,invisible=it.id!=0,tile=if(it.id==0)0 else 1,history=listOf(if(it.id==0)0 else 1)) })
        s=RaceBots.act(s); assertFalse(s.players.any { it.stunned }); assertEquals(1,s.actor.inventory.size)
    }
}
