package tv.enktel.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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

private const val NOTIFY_EMAIL = "info@enktel.tv"
private const val PRICE = "\$99 USD"
private const val PLAN = "12-month full access"

/**
 * The hosted PayPal checkout, preselected to the 12-month plan.
 *
 * This screen used to hand out a PayPal address and ask the buyer to send a
 * personal "Friends & Family" payment, then email a screenshot so the account
 * could be activated by hand. That is a worse deal for everyone: a personal
 * transfer carries no purchase protection for the buyer, PayPal's User
 * Agreement does not permit it for goods and services, and receiving
 * commercial payments that way is a common trigger for account limitation on
 * the seller's side. It also could not be automatic — every sale needed a
 * human to read an email and provision a line.
 *
 * The site already had a full Orders v2 business integration behind
 * /checkout, which captures the payment and provisions the subscription in the
 * same request. Pointing at it makes the sale a real goods-and-services
 * transaction and removes the manual step entirely.
 */
private val CHECKOUT_URL = BuildConfig.EAGLE_UPGRADE_URL

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
                    "Secure PayPal checkout",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pay $PRICE by card or PayPal balance. Your account is created " +
                        "and activated automatically as soon as the payment clears — " +
                        "there is nothing to send us.",
                    color = EnktelTextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))

                if (isMobile) {
                    FocusButton("Pay $PRICE — Secure Checkout", accent = true, onClick = {
                        val opened = runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, CHECKOUT_URL.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.isSuccess
                        // A device with no browser is rare on a phone but not
                        // impossible, and a button that silently does nothing
                        // is the failure this app has shipped before. Show the
                        // address so the sale is still reachable.
                        if (!opened) formError = "No browser found — open $CHECKOUT_URL on your phone."
                    })
                } else {
                    // A sideloaded Fire TV Stick often has no browser at all,
                    // so ACTION_VIEW here would throw and be swallowed. A QR
                    // code moves the checkout to the one device in the room
                    // that definitely can complete it, and is also simply
                    // easier than typing card details with a D-pad.
                    val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?" +
                        "size=400x400&margin=10&data=" + URLEncoder.encode(CHECKOUT_URL, "UTF-8")
                    Text(
                        "Scan with your phone to pay securely:",
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
                            contentDescription = "Checkout QR",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // The QR is useless to anyone whose phone camera will not
                    // read it, and unreadable to a screen reader.
                    Text(
                        CHECKOUT_URL,
                        color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
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
                    "Paid but not activated?",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "You should not need this. Checkout activates the account itself and " +
                        "emails your login details. Use this only if you have paid and " +
                        "nothing arrived — send us your details and we will sort it by hand.",
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
                    if (sent) "Re-send" else "Contact support",
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
                        val subject = "EnkTel 4K — paid but not activated"
                        val body = buildString {
                            appendLine("EnkTel 4K — 12 Month Upgrade Request")
                            appendLine()
                            appendLine("Name: $contactName")
                            appendLine("Email: $userEmail")
                            appendLine("Plan: $PLAN ($PRICE)")
                            appendLine("Paid via: PayPal checkout ($CHECKOUT_URL)")
                            if (paymentRef.isNotBlank()) appendLine("Transaction ref: $paymentRef")
                            appendLine()
                            appendLine("I paid through the checkout but my account is not active.")
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
                    "Your account is created the moment PayPal confirms the payment, and " +
                        "your login details are emailed to you straight away — usually " +
                        "within a minute. If PayPal holds the payment for review, which it " +
                        "occasionally does, activation follows once it clears; that can take " +
                        "2–3 hours and is out of our hands.",
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
