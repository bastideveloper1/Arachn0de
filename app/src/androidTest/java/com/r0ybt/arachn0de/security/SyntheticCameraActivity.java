package com.r0ybt.arachn0de.security;

/** Test APK only. Framework-only Java also runs under its independent foreign UID. */
public class SyntheticCameraActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.net.Uri uri=getIntent().getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);
        android.graphics.Bitmap bitmap=null;
        boolean ok=false;
        try {
            if(uri==null) throw new IllegalArgumentException("Missing synthetic output");
            bitmap=android.graphics.Bitmap.createBitmap(160,80,android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff2865aa);
            try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"w")) {
                if(out==null) throw new java.io.IOException("Missing synthetic output stream");
                ok=bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,out);
                out.flush();
            }
        } catch(Exception failure) {
            android.util.Log.w("SyntheticCamera","Synthetic capture failed");
        } finally {
            if(bitmap!=null) bitmap.recycle();
            sendBroadcast(new android.content.Intent("com.r0ybt.arachn0de.PHASE_C_CAMERA_DONE")
                .setPackage(getIntent().getStringExtra("returnPackage"))
                .putExtra("token",getIntent().getStringExtra("token"))
                .putExtra("ok",ok).putExtra("uid",android.os.Process.myUid()));
            setResult(ok?RESULT_OK:RESULT_CANCELED);
            finish();
        }
    }
}
