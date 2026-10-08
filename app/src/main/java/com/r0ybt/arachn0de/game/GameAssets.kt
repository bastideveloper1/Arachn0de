package com.r0ybt.arachn0de.game

import com.r0ybt.arachn0de.R

/** Old PNGs remain battle art. Roles can be replaced independently. */
data class CharacterArt(val normalSprite: Int, val battleSprite: Int,
    val portrait: Int = normalSprite, val token: Int = normalSprite, val attackArt: Int = battleSprite,
    val abilityArt: Int = battleSprite, val hurtArt: Int = normalSprite, val victoryArt: Int = normalSprite)
internal fun RatCharacter.art(): CharacterArt = when (this) {
    RatCharacter.KNIGHT -> CharacterArt(R.drawable.normal_caballero, R.drawable.caballero)
    RatCharacter.MAGE -> CharacterArt(R.drawable.normal_mago, R.drawable.mago)
    RatCharacter.HUNTRESS -> CharacterArt(R.drawable.normal_arquero, R.drawable.cazadora)
    RatCharacter.NECROMANCER -> CharacterArt(R.drawable.normal_nigromante, R.drawable.nigromante)
    RatCharacter.MONK -> CharacterArt(R.drawable.normal_monje, R.drawable.monje)
    RatCharacter.ROGUE -> CharacterArt(R.drawable.normal_picaro, R.drawable.picaro)
}
internal fun ObjectType.drawable(): Int = when (this) {
    ObjectType.BANNER -> R.drawable.estandarte
    ObjectType.ICE_BARRIER -> R.drawable.barrerahielo
    ObjectType.SPIKES -> R.drawable.espinas
    ObjectType.TOTEM -> R.drawable.totem
    ObjectType.BEAR_TRAP -> R.drawable.trampaoso
    ObjectType.ZOMBIE -> R.drawable.zombie
}
internal fun diceDrawable(face: Int): Int = when (face) {
    1 -> R.drawable.dados1; 2 -> R.drawable.dados2; 3 -> R.drawable.dados3
    4 -> R.drawable.dados4; 5 -> R.drawable.dados5; 6 -> R.drawable.dados6
    else -> error("D6 face out of range")
}
