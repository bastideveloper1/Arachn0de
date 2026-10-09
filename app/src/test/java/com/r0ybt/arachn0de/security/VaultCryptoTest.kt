package com.r0ybt.arachn0de.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VaultCryptoTest {
    private fun encrypted(bytes: ByteArray, key: ByteArray, purpose: String = "store/file"): ByteArray =
        ByteArrayOutputStream().also { VaultCrypto.encrypt(ByteArrayInputStream(bytes), it, key, purpose) }.toByteArray()
    private fun decrypted(bytes: ByteArray, key: ByteArray, purpose: String = "store/file"): ByteArray =
        ByteArrayOutputStream().also { VaultCrypto.decrypt(ByteArrayInputStream(bytes), it, key, purpose) }.toByteArray()
    private fun rejected(work: () -> Unit) {
        try { work(); fail("Authentication must fail") } catch (_: VaultAuthenticationException) {}
    }
    @Test fun passwordUtf8WrapAndChangePreserveIndependentDataKey() {
        val id = UUID.randomUUID(); val key = VaultCrypto.randomKey()
        val old = "contraseña 🕷️".toCharArray(); val next = "otra contraseña".toCharArray()
        val first = VaultCrypto.wrap(id, old, key)
        assertArrayEquals(key,VaultCrypto.unwrap(id, old, first))
        rejected { VaultCrypto.unwrap(id,next,first) }
        val changed = VaultCrypto.wrap(id,next,VaultCrypto.unwrap(id,old,first))
        assertArrayEquals(key,VaultCrypto.unwrap(id,next,changed))
        rejected { VaultCrypto.unwrap(id,old,changed) }
        val file = encrypted(byteArrayOf(1,2,3),key)
        assertArrayEquals(byteArrayOf(1,2,3),decrypted(file,VaultCrypto.unwrap(id,next,changed)))
        old.fill('\u0000'); next.fill('\u0000'); key.fill(0)
    }
    @Test fun wrapHasFreshSaltNonceAndAuthenticatedStoreAndParameters() {
        val id=UUID.randomUUID(); val key=VaultCrypto.randomKey(); val password="secret".toCharArray()
        val first=VaultCrypto.wrap(id,password,key); val second=VaultCrypto.wrap(id,password,key)
        assertFalse(first.copyOfRange(36,68).contentEquals(second.copyOfRange(36,68)))
        assertFalse(first.copyOfRange(68,80).contentEquals(second.copyOfRange(68,80)))
        rejected { VaultCrypto.unwrap(UUID.randomUUID(),password,first) }
        for (offset in listOf(0,8,24,36,68,80,127)) {
            val corrupt=first.copyOf(); corrupt[offset]=(corrupt[offset].toInt() xor 1).toByte()
            rejected { VaultCrypto.unwrap(id,password,corrupt) }
        }
        rejected { VaultCrypto.unwrap(id,password,first+byteArrayOf(0)) }
    }
    @Test fun streamRoundTripsEmptyBoundaryAndLargeVolumesWithoutFullBuffering() {
        val key=VaultCrypto.randomKey()
        for(size in listOf(0,1,65535,65536,65537,3*1024*1024)) {
            val bytes=ByteArray(size) { (it*31).toByte() }
            assertArrayEquals(bytes,decrypted(encrypted(bytes,key),key))
        }
    }
    @Test fun streamRejectsWrongStoreKeyPurposeTamperingTruncationAndTrailingData() {
        val key=VaultCrypto.randomKey(); val cipher=encrypted(ByteArray(130000){42},key)
        rejected { decrypted(cipher,VaultCrypto.randomKey()) }
        rejected { decrypted(cipher,key,"another-store/file") }
        for(offset in listOf(0,8,39,40,44,100,cipher.lastIndex)) {
            val corrupt=cipher.copyOf(); corrupt[offset]=(corrupt[offset].toInt() xor 1).toByte()
            rejected { decrypted(corrupt,key) }
        }
        for(size in listOf(0,39,40,44,cipher.size-1,cipher.size-20)) rejected { decrypted(cipher.copyOf(size),key) }
        rejected { decrypted(cipher+byteArrayOf(0),key) }
    }
    @Test fun recordsCannotBeReorderedDuplicatedOrTransplanted() {
        val key=VaultCrypto.randomKey(); val bytes=ByteArray(2*65536){ (it%251).toByte() }
        val a=encrypted(bytes,key); val b=encrypted(bytes,key)
        assertFalse(a.contentEquals(b))
        val record=4+65536+16
        rejected { decrypted(a.copyOfRange(0,40)+a.copyOfRange(40+record,40+2*record)+a.copyOfRange(40,40+record)+a.copyOfRange(40+2*record,a.size),key) }
        rejected { decrypted(a.copyOfRange(0,40+record)+a.copyOfRange(40,40+record)+a.copyOfRange(40+2*record,a.size),key) }
        rejected { decrypted(a.copyOfRange(0,40)+b.copyOfRange(40,40+record)+a.copyOfRange(40+record,a.size),key) }
    }
    @Test fun hkdfSeparatesStoresAndPurposes() {
        val master=VaultCrypto.randomKey(); val salt=VaultCrypto.randomKey()
        val db=VaultCrypto.subkey(master,salt,"database")
        assertArrayEquals(db,VaultCrypto.subkey(master,salt,"database"))
        assertFalse(db.contentEquals(VaultCrypto.subkey(master,salt,"files")))
        assertFalse(db.contentEquals(VaultCrypto.subkey(master,VaultCrypto.randomKey(),"database")))
        assertFalse(db.contentEquals(VaultCrypto.subkey(VaultCrypto.randomKey(),salt,"database")))
    }
    @Test fun aes256GcmMatchesPublishedZeroKeyVector() {
        // AES-256-GCM, all-zero key/IV/plaintext, empty AAD (NIST standard vector).
        val cipher=VaultCrypto.gcm(javax.crypto.Cipher.ENCRYPT_MODE,ByteArray(32),ByteArray(12),byteArrayOf(),ByteArray(16))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919",cipher.joinToString("") {"%02x".format(it)})
    }

}
