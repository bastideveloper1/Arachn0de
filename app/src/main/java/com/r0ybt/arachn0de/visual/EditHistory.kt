package com.r0ybt.arachn0de.visual

/** Bounded gesture history shared by image annotations and diagrams; validation precedes mutation. */
internal class EditHistory<T>(initial:T,private val validate:(T)->Unit) {
    private val past=ArrayDeque<T>();private val future=ArrayDeque<T>();private var coalescing:String?=null
    var current=initial;private set
    val canUndo get()=past.isNotEmpty();val canRedo get()=future.isNotEmpty()
    fun commit(next:T,key:String?=null) {if(next!=current) {validate(next);if(key==null||key!=coalescing) {past.addLast(current);if(past.size>40)past.removeFirst()};current=next;coalescing=key;future.clear()}}
    fun undo():T {coalescing=null;if(canUndo) {future.addLast(current);current=past.removeLast()};return current}
    fun redo():T {coalescing=null;if(canRedo) {past.addLast(current);current=future.removeLast()};return current}
}
