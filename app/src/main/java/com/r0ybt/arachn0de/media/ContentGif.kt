package com.r0ybt.arachn0de.media

/** Bounded structural verification before a GIF reaches a native decoder or a restored store. */
internal object ContentGif {
    /** Preserve image data, palettes, transparency, timing and looping; omit opaque extensions.
     * Validated plain-text graphics remain part of the visible animation. */
    fun publicBytes(bytes:ByteArray):ByteArray {
        inspect(bytes)
        var at=13
        if(bytes[10].toInt() and 128!=0) at+=3*(1 shl ((bytes[10].toInt() and 7)+1))
        val output=java.io.ByteArrayOutputStream();output.write(bytes,0,at)
        fun byte():Int {require(at<bytes.size);return bytes[at++].toInt() and 255}
        fun skip(n:Int) {require(n>=0 && n<=bytes.size-at);at+=n}
        fun blocks() {while(true) {val n=byte();if(n==0)break;skip(n)}}
        while(true) {
            val start=at
            when(byte()) {
                0x21->{
                    when(byte()) {
                        0xf9->{require(byte()==4);val flags=byte();require(flags and 0xe0==0 && (flags shr 2 and 7)<=3);skip(3);require(byte()==0);output.write(bytes,start,at-start)}
                        0x01->{
                            require(byte()==12)
                            fun word():Int {val low=byte();return low or (byte() shl 8)}
                            val x=word();val y=word();val w=word();val h=word();val cw=byte();val ch=byte();val fg=byte();val bg=byte()
                            val info=inspect(bytes);val palette=if(bytes[10].toInt() and 128!=0)1 shl ((bytes[10].toInt() and 7)+1) else 0
                            require(w>0 && h>0 && x+w<=info.width && y+h<=info.height && cw>0 && ch>0 && fg<palette && bg<palette)
                            var text=0;while(true) {val n=byte();if(n==0)break;text+=n;require(text.toLong()<=(w/cw).toLong()*(h/ch));skip(n)}
                            output.write(bytes,start,at-start)
                        }
                        0xff->{
                            val n=byte();require(n==11);val name=String(bytes,at,n,Charsets.US_ASCII);skip(n)
                            if(name=="NETSCAPE2.0" || name=="ANIMEXTS1.0") {
                                require(byte()==3 && byte()==1);val low=byte();val high=byte();require(byte()==0)
                                output.write(byteArrayOf(0x21,0xff.toByte(),11)+"NETSCAPE2.0".toByteArray()+byteArrayOf(3,1,low.toByte(),high.toByte(),0))
                            } else blocks()
                        }
                        0xfe->blocks()
                        else->blocks() // Unknown extensions have no defined rendering semantics.
                    }
                }
                0x2c->{skip(8);val flags=byte();if(flags and 128!=0)skip(3*(1 shl ((flags and 7)+1)));skip(1);blocks();output.write(bytes,start,at-start)}
                0x3b->{output.write(0x3b);return output.toByteArray().also {inspect(it)}}
                else->error("GIF inválido.")
            }
        }
    }
    data class Info(val width:Int,val height:Int,val frames:Int)
    fun inspect(bytes:ByteArray):Info {
        require(bytes.size in 14..20*1024*1024) {"GIF inválido o demasiado grande."}
        val header=bytes.copyOfRange(0,6).toString(Charsets.US_ASCII)
        require(header=="GIF87a" || header=="GIF89a") {"GIF inválido."}
        var at=6
        fun byte():Int {require(at<bytes.size) {"GIF incompleto."};return bytes[at++].toInt() and 255}
        fun word()=byte() or (byte() shl 8)
        fun skip(n:Int) {require(n>=0 && at+n<=bytes.size) {"GIF incompleto."};at+=n}
        fun blocks() {while(true) {val n=byte();if(n==0)break;skip(n)}}
        val w=word();val h=word();require(w in 1..2048 && h in 1..2048) {"El GIF admite hasta 2048 × 2048 píxeles."}
        val flags=byte();skip(2);if(flags and 128!=0)skip(3*(1 shl ((flags and 7)+1)))
        var frames=0
        while(true)when(byte()) {
            0x21->{byte();blocks()}
            0x2c->{val x=word();val y=word();val iw=word();val ih=word();require(iw>0 && ih>0 && x+iw<=w && y+ih<=h)
                val local=byte();if(local and 128!=0)skip(3*(1 shl ((local and 7)+1)))
                require(byte() in 2..8);blocks();frames++;require(frames<=1000) {"El GIF supera 1000 fotogramas."}}
            0x3b->{require(frames>0 && at==bytes.size) {"GIF inválido."};return Info(w,h,frames)}
            else->error("GIF inválido.")
        }
    }
}
