package com.reelcounter.core.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.RcTheme

/**
 * The app's text field: a calm outlined field with a visible label (never placeholder-only), an
 * optional leading icon, and the error shown right under it. [password] adds a show/hide toggle.
 */
@Composable
fun RcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    error: String? = null,
    supporting: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        isError = error != null,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = when {
            trailing != null -> trailing
            password -> {
                {
                    IconButton(onClick = { shown = !shown }) {
                        Icon(
                            if (shown) RcIcons.NotSeen else RcIcons.Seen,
                            contentDescription = if (shown) "Hide password" else "Show password",
                        )
                    }
                }
            }
            else -> null
        },
        supportingText = (error ?: supporting)?.let { text -> { Text(text) } },
        visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = !password && keyboardType == KeyboardType.Text,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (sticker) scheme.onSurface else scheme.primary,
            unfocusedBorderColor = if (sticker) scheme.onSurface.copy(alpha = 0.55f) else scheme.outline,
            focusedLabelColor = if (sticker) scheme.onSurface else scheme.primary,
            cursorColor = if (sticker) scheme.onSurface else scheme.primary,
            focusedContainerColor = scheme.surfaceContainer,
            unfocusedContainerColor = scheme.surfaceContainer,
            disabledContainerColor = scheme.surfaceContainer,
        ),
    )
}

/** A short error or notice shown above a form's main button. */
@Composable
fun FormMessage(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) extended.warningText else extended.successText,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Min touch height for text links in forms. */
val LinkMinHeight = 44.dp
