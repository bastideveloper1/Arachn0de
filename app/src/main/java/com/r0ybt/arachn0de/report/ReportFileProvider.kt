package com.r0ybt.arachn0de.report

import androidx.core.content.FileProvider

/** A concrete provider avoids OEM issues with declaring the AndroidX base class directly. */
class ReportFileProvider : FileProvider()
