package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import com.r0ybt.arachn0de.domain.model.AttachmentReferences

/** Keep Material's outlined decoration; hit testing uses the actual inner text coordinates. */
@Composable
internal fun AttachmentDescriptionField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean, focusRequester: androidx.compose.ui.focus.FocusRequester? = null, transformation: AttachmentVisualTransformation, onReference: (Int) -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val transformed = transformation.filter(AnnotatedString(value.text))
    val references = AttachmentReferences.matches(value.text).toList()
    BasicTextField(value = value, onValueChange = onValueChange, enabled = enabled,
        modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).fillMaxWidth().defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight), interactionSource = interactions,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = transformation,
        onTextLayout = { layout = it }, decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(value = value.text, enabled = enabled, singleLine = false,
                visualTransformation = transformation, interactionSource = interactions, label = { Text("Descripción") },
                innerTextField = {
                    Box(Modifier.testTag("description-inner").pointerInput(value.text, enabled, transformed.text) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val result = layout
                            val offset = result?.getOffsetForPosition(down.position)
                            val reference = if (!enabled || result == null || offset == null || offset >= transformed.text.length ||
                                !result.getBoundingBox(offset).contains(down.position)) null else references.firstOrNull {
                                offset >= transformed.offsetMapping.originalToTransformed(it.range.first) &&
                                    offset < transformed.offsetMapping.originalToTransformed(it.range.last + 1)
                            }
                            if (reference != null) {
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                                    (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                                    up.consume(); onReference(reference.range.first)
                                }
                            }
                        }
                    }) { inner() }
                })
        })
}
