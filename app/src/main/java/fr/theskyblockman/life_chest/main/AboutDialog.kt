package fr.theskyblockman.life_chest.main

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import fr.theskyblockman.life_chest.R

@Composable
fun AboutDialog(activity: Activity, onDismiss: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.outline_shield_with_heart_24),
                contentDescription = stringResource(R.string.app_icon)
            )
        },
        title = {
            Text(text = stringResource(id = R.string.app_name))
        },
        text = {
            val text = buildAnnotatedString {
                pushStyle(style = SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = null
                ))
                val parts = stringResource(R.string.legalese).split("|mit_license_link_name|")
                append(parts.first())
                pushStringAnnotation(
                    tag = "link",
                    annotation = "https://github.com/theskyblockman/life-chest-compose/blob/master/LICENSE"
                )
                withLink(LinkAnnotation.Url("https://github.com/theskyblockman/life-chest-compose/blob/master/LICENSE", TextLinkStyles(style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )))) {
                    append(stringResource(R.string.mit_license_link_name))
                }
                pop()
                append(parts.last())
            }

            Text(text)
        },
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(
                onClick = {
                    activity.startActivity(Intent(activity, OssLicensesMenuActivity::class.java))
                }
            ) {
                Text(stringResource(R.string.show_licenses))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}