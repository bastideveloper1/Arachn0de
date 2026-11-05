package com.r0ybt.arachn0de.guest

import android.content.Context
import android.content.Intent
import com.r0ybt.arachn0de.Arachn0deApplication

internal object GuestCaptureStart {
    // Activation is deliberately gated until the restricted Activity/lifecycle has been validated on Android.
    const val VERIFIED=false
    suspend fun begin(context:Context,person:String) {
        check(VERIFIED) {"Captura de invitado pendiente de validación de aislamiento en Android."}
        val app=context.applicationContext as Arachn0deApplication
        val session=requireNotNull(app.security.current.value)
        check(session.primary)
        require(session.database.personDao().get(person)!=null)
        val queue=GuestCaptureQueue(app)
        val id=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {queue.prepare(session.access.id.toString(),person)}
        check(app.security.current.value===session)
        app.security.lock()
        check(app.security.current.value==null)
        context.startActivity(Intent(context,GuestCaptureActivity::class.java).putExtra("capture",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
