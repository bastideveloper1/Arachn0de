package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.DescriptionParser

/** Prefix only the chosen lines; existing whitespace, references and other lines keep their bytes. */
internal fun EditorDraft.applyDescriptionList(alphabetic:Boolean):Boolean {
    val start=minOf(descriptionSelectionStart,descriptionSelectionEnd).coerceIn(0,description.length)
    val end=maxOf(descriptionSelectionStart,descriptionSelectionEnd).coerceIn(0,description.length)
    val from=description.lastIndexOf('\n',start-1)+1
    val last=if(end>start && description.getOrNull(end-1)=='\n')end-1 else end
    val to=description.indexOf('\n',last).let {if(it<0)description.length else it}
    if(DescriptionParser.formatRanges(description).any {
        it.format==DescriptionParser.Format.Code && from<=it.end && to>=it.start
    })return false
    var number=0
    var letter="a"
    var previousLetter:String?=null
    val lines=description.substring(from,to).split('\n')
    val replacement=lines.joinToString("\n") {line->
        if(line.isBlank() && lines.size>1) {previousLetter=null;line} else {
            val indent=line.takeWhile {it==' ' || it=='\t'}
            val content=line.drop(indent.length)
            val prefix=Regex("^(-|[0-9]+\\.|[a-zA-Z]+\\.)[ \\t]").find(content)
            val letters=prefix?.groupValues?.get(1)?.removeSuffix(".")?.takeIf {it.all(Char::isLetter)}
            val isMarker=prefix!=null && (letters==null || letters.length==1 || previousLetter?.let {nextDescriptionLetter(it)==letters}==true)
            previousLetter=letters
            val body=if(isMarker) content.drop(requireNotNull(prefix).value.length) else content
            val marker=if(alphabetic) letter.also {letter=nextDescriptionLetter(letter)} else (++number).toString()
            "$indent$marker. $body"
        }
    }
    description=description.replaceRange(from,to,replacement)
    descriptionSelectionStart=from+replacement.length
    descriptionSelectionEnd=descriptionSelectionStart
    pendingDescriptionList=null
    return true
}

private fun nextDescriptionLetter(word:String):String {
    val letters=word.toCharArray();val first=if(word.first().isUpperCase())'A' else 'a';val last=first+25
    var i=letters.lastIndex
    while(i>=0 && letters[i]==last) {letters[i]=first;i--}
    if(i<0)return first+letters.concatToString()
    letters[i]++;return letters.concatToString()
}

/** Only a newly typed Enter continues a list. Opening, pasting and IME composition are untouched. */
internal fun continueDescriptionList(old:String,next:String,start:Int,end:Int,composing:Boolean=false):Pair<String,Int> {
    if(composing || start!=end || start<1 || next.length!=old.length+1 || next[start-1]!='\n' ||
        next.removeRange(start-1,start)!=old)return next to start
    val at=start-1
    if(DescriptionParser.formatRanges(old).any {it.format==DescriptionParser.Format.Code && at>it.start && at<=it.end})return next to start
    val lineStart=old.lastIndexOf('\n',at-1)+1
    val line=old.substring(lineStart,at)
    val match=Regex("^([ \\t]*)(-|[0-9]+\\.|[a-zA-Z]+\\.) (.*)$").matchEntire(line) ?: return next to start
    val (indent,marker,body)=match.destructured
    val following=when {
        marker=="-"->"-"
        marker.first().isDigit()->(marker.dropLast(1).toLongOrNull()?.takeIf {it<Long.MAX_VALUE}?.plus(1)?.toString() ?: return next to start)+"."
        else->{
            val letters=marker.dropLast(1)
            if(letters.length>1) {
                val previous=old.substring(0,(lineStart-1).coerceAtLeast(0)).substringAfterLast('\n')
                val prior=Regex("^([ \t]*)([a-zA-Z]+)\\. .*$").matchEntire(previous)
                if(prior==null || prior.groupValues[1]!=indent || nextDescriptionLetter(prior.groupValues[2])!=letters)return next to start
            }
            nextDescriptionLetter(letters)+"."
        }
    }
    if(body.isBlank())return next.removeRange(lineStart,start) to lineStart
    val insertion=indent+following+" "
    return next.substring(0,start)+insertion+next.substring(start) to (start+insertion.length)
}
