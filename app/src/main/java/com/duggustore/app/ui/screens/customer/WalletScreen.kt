package com.duggustore.app.ui.screens.customer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.WalletTransaction
import com.duggustore.app.data.repository.walletBalance
import com.duggustore.app.ui.components.ListSkeleton
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.theme.*

@Composable
fun WalletScreen(
    transactions: List<WalletTransaction>,
    referralCode: String = "",
    onBack: () -> Unit,
    isLoading: Boolean = false
) {
    val balance = transactions.walletBalance()

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        ScreenHeader(
            title = stringResource(R.string.wallet_title),
            onBack = onBack
        ) {
            // The balance belongs in the teal rather than in a card below it:
            // it is the answer to the only question this screen is asked, so
            // it reads as the header's subject instead of as the first row of
            // a list it sits on top of.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SectionGap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                Text("₹$balance", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.wallet_usable),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        if (referralCode.isNotBlank()) {
            ReferralCard(referralCode)
        }

        if (isLoading) {
            ListSkeleton()
        } else if (transactions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.wallet_empty_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    stringResource(R.string.wallet_empty_subtitle),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { it.id }) { txn -> WalletTransactionRow(txn) }
            }
        }
    }
}

@Composable
private fun ReferralCard(referralCode: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = Dimens.ShapeMd,
        color = OrangeSurface
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CardGiftcard, null, tint = OrangeDark, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.wallet_refer_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(stringResource(R.string.wallet_your_code, referralCode), fontSize = 13.sp, color = TextSecondary)
            }
            val shareMessage = stringResource(R.string.wallet_share_message, referralCode)
            val shareChooserTitle = stringResource(R.string.wallet_share_chooser)
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                    }
                    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.wallet_share), tint = OrangeDark)
            }
        }
    }
}

@Composable
private fun WalletTransactionRow(txn: WalletTransaction) {
    val isCredit = txn.type == "CREDIT"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.ShapeMd,
        color = SurfaceWhite,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background((if (isCredit) SuccessGreen else Coral).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (isCredit) SuccessGreen else Coral,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(txn.createdAt.take(10), fontSize = 11.sp, color = TextLight)
            }
            Text(
                text = "${if (isCredit) "+" else "-"}₹${txn.amount}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) SuccessGreen else Coral
            )
        }
    }
}
