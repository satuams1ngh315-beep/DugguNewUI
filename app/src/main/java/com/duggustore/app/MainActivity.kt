package com.duggustore.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.duggustore.app.platform.RazorpayPay
import com.duggustore.app.platform.withAppLanguage
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.duggustore.app.ui.navigation.AppNavGraph
import com.duggustore.app.ui.navigation.Screen
import com.duggustore.app.ui.theme.DugguStoreTheme
import com.duggustore.app.ui.viewmodel.AuthViewModel
import com.duggustore.app.data.remote.AuthDeepLinkParser
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    // Held by the activity so the deep link and the UI act on the same instance.
    private val authViewModel: AuthViewModel by viewModels()

    // Set from the launching/new intent when the app was opened by tapping a
    // push notification (see DugguFcmService), read by the nav graph below.
    private val pendingNotificationRoute = MutableStateFlow<String?>(null)

    /**
     * Everything the activity resolves — including every string Compose reads
     * through stringResource — comes from this context, so applying the chosen
     * language here is what makes the whole app switch.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        handlePushNotificationIntent(intent)
        setContent {
            DugguStoreTheme {
                // Below Android 13 a notification just shows, no permission
                // asked; from 13 on it's a normal runtime permission like
                // camera or location, so it needs the same launcher pattern.
                val context = LocalContext.current
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // enableEdgeToEdge() stops the window resizing itself for the
                // keyboard, so adjustResize in the manifest no longer moves
                // anything on its own: the IME inset is handed to Compose and
                // has to be consumed here, or whatever sits at the bottom of a
                // screen ends up underneath the keyboard.
                //
                // While the keyboard is up it covers the navigation bar too, so
                // the navigation-bar inset is consumed at the same time —
                // otherwise the screens that pad for it themselves would add
                // that height a second time and float above the keyboard.
                //
                // The raw inset shrinks and grows on every frame of the
                // keyboard's slide animation, and reading it directly here —
                // above the whole nav graph — recomposed the entire visible
                // screen on every one of those frames, which is what showed
                // up as jank each time the keyboard opened or closed,
                // anywhere in the app. derivedStateOf collapses that down to
                // the boolean actually flipping, so this only recomposes
                // once per open/close instead of once per animation frame.
                val density = LocalDensity.current
                // WindowInsets.ime is itself a @Composable getter, so it has
                // to be read here, in composable context — only the plain
                // getBottom() call on the result can live inside
                // derivedStateOf's (non-composable) calculation lambda.
                val imeInsets = WindowInsets.ime
                val imeVisible by remember(density, imeInsets) {
                    derivedStateOf { imeInsets.getBottom(density) > 0 }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .then(
                            if (imeVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                            else Modifier
                        )
                ) {
                    val navController = rememberNavController()
                    val notificationRoute = pendingNotificationRoute.value
                    AppNavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        pendingNotificationRoute = notificationRoute,
                        onNotificationRouteConsumed = { pendingNotificationRoute.value = null }
                    )
                }
            }
        }
    }

    /** launchMode=singleTask means a link arriving while the app is open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
        handlePushNotificationIntent(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val link = AuthDeepLinkParser.parse(intent?.data) ?: return
        authViewModel.onAuthDeepLink(link)
        // Clear it so a configuration change does not replay the same link.
        intent?.data = null
    }

    /** DugguFcmService points every push's tap target at this activity with this extra set. */
    private fun handlePushNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_notifications", false) != true) return
        pendingNotificationRoute.value = Screen.CustomerNotifications.route
        // Clear it so a configuration change does not replay the same navigation.
        intent.removeExtra("open_notifications")
    }

    // ---- Razorpay Checkout result (the sheet reports back to the host activity) ----

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        if (paymentData == null) {
            RazorpayPay.postFailure(-1, "Payment succeeded but the receipt was empty — contact support")
        } else {
            RazorpayPay.postSuccess(paymentData)
        }
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        RazorpayPay.postFailure(code, description)
    }
}
