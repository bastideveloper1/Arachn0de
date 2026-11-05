package com.r0ybt.arachn0de.domain.model

/** Normal core tasks; deliberately independent from the five Sprint phases. */
enum class TaskStatus(val label:String) {
    PENDING("Pendiente"), VERIFY("Por verificar"), COMPLETED("Completada");
    companion object {
        fun available(verificationEnabled:Boolean) = entries.filter {verificationEnabled || it!=VERIFY}
    }
}
