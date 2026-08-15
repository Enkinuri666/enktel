package tv.enktel.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.enktel.app.BuildConfig
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim
import java.net.URLEncoder

private const val PAYPAL_EMAIL = "mpetr930@gmail.com"
private const val NOTIFY_EMAIL = "info@enktel.tv"
private const val PRICE = "\$99 USD"
private const val PLAN = "12-month full access"

@Composable
fun UpgradeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val isMobile = BuildConfig.FLAVOR == "mobile"

    var userEmail by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var paymentRef by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Upgrade to EnkTel 4K",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$PLAN — $PRICE",
                color = EnktelBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            // ── Payment instructions ───────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EnktelBlue.copy(alpha = 0.10f))
                    .border(1.dp, EnktelBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "Step 1 — Send payment via PayPal",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open PayPal and send $PRICE to:",
                    color = EnktelTextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    PAYPAL_EMAIL,
                    color = EnktelBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "IMPORTANT: Use \"Send to a Friend\" (personal payment), " +
                        "NOT \"Pay for Goods or Services\". Leave the payment amount " +
                        "field empty until you are on the PayPal send screen, then enter " +
                        "$PRICE. All payments must be in USD.",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))

                if (isMobile) {
                    FocusButton("Open PayPal", accent = true, onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/myaccount/transfer/homepage/pay"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    })
                } else {
                    val paypalUrl = "https://www.paypal.com/myaccount/transfer/homepage/pay"
                    val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?" +
                        "size=400x400&margin=10&data=" + URLEncoder.encode(paypalUrl, "UTF-8")
                    Text(
                        "Scan with your phone to open PayPal:",
                        color = EnktelTextDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .widthIn(max = 240.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = qrUrl,
                            contentDescription = "PayPal QR",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Contact form ───────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EnktelSurface.copy(alpha = 0.7f))
                    .border(1.dp, EnktelBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "Step 2 — Send your details + proof of payment",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fill in your email and name below, then tap \"Send\" to email your " +
                        "proof of payment to $NOTIFY_EMAIL. We'll activate your account.",
                    color = EnktelTextDim, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                TvTextField(contactName, { contactName = it }, "Your name")
                Spacer(Modifier.height(10.dp))
                TvTextField(userEmail, { userEmail = it }, "Your email address")
                Spacer(Modifier.height(10.dp))
                TvTextField(paymentRef, { paymentRef = it }, "PayPal transaction ID or screenshot note (optional)")
                Spacer(Modifier.height(12.dp))
                if (formError.isNotBlank()) {
                    Text(formError, color = EnktelLive, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                if (sent) {
                    Text(
                        "Email sent — check your email app to finish sending.",
                        color = EnktelOk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                FocusButton(
                    if (sent) "Re-send" else "Send payment notification",
                    accent = true,
                    onClick = {
                        formError = ""
                        if (userEmail.isBlank() || !userEmail.contains("@")) {
                            formError = "Please enter a valid email address."
                            return@FocusButton
                        }
                        if (contactName.isBlank()) {
                            formError = "Please enter your name."
                            return@FocusButton
                        }
                        val subject = "EnkTel 4K Upgrade — Payment Notification"
                        val body = buildString {
                            appendLine("EnkTel 4K — 12 Month Upgrade Request")
                            appendLine()
                            appendLine("Name: $contactName")
                            appendLine("Email: $userEmail")
                            appendLine("Plan: $PLAN ($PRICE)")
                            appendLine("PayPal sent to: $PAYPAL_EMAIL")
                            if (paymentRef.isNotBlank()) appendLine("Transaction ref: $paymentRef")
                            appendLine()
                            appendLine("Please activate my account. Thank you!")
                        }
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(NOTIFY_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        val ok = runCatching {
                            ctx.startActivity(Intent.createChooser(intent, "Send payment notification"))
                        }.isSuccess
                        sent = ok
                        if (!ok) formError = "No email app found — please email $NOTIFY_EMAIL manually."
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── After-payment message ──────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EnktelOk.copy(alpha = 0.10f))
                    .border(1.dp, EnktelOk.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "After payment",
                    color = EnktelOk, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Account activation is usually within minutes, but can take " +
                        "up to 2–3 hours due to PayPal processing times. " +
                        "You will receive an email confirmation once your account is active.",
                    color = Color.White, fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "If you have questions, contact us at $NOTIFY_EMAIL.",
                    color = EnktelTextDim, fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(20.dp))
            FocusButton("Done", accent = false, onClick = { nav.popBackStack() })
        }
    }
}
