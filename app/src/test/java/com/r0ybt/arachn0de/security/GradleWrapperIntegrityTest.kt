package com.r0ybt.arachn0de.security

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties

class GradleWrapperIntegrityTest {
    @Test fun configuredBinaryDistributionMatchesOfficialGradleChecksumWithoutChangingVersions() {
        val root=generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {it.parentFile}
            .first {File(it,"gradle/wrapper/gradle-wrapper.properties").isFile}
        val properties=Properties().apply {File(root,"gradle/wrapper/gradle-wrapper.properties").inputStream().use {load(it)}}
        assertEquals("https://services.gradle.org/distributions/gradle-9.6.0-bin.zip",properties.getProperty("distributionUrl"))
        // Official publication: https://gradle.org/release-checksums/ — 9.6.0, Binary-only ZIP.
        assertEquals("bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01",properties.getProperty("distributionSha256Sum"))
        assertEquals("true",properties.getProperty("validateDistributionUrl"))
    }
}
