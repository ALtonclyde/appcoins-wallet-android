package com.asfoundation.wallet.topup

import android.net.Uri
import android.os.Bundle
import com.asfoundation.wallet.billing.adyen.PaymentType

interface TopUpActivityView {
  fun showTopUpScreen()

  fun navigateToAdyenPayment(paymentType: PaymentType, data: TopUpData, selectedCurrency: String,
                             origin: String, transactionType: String, bonusValue: String)

  fun navigateToLocalPayment(paymentMethod: String, data: TopUpData, selectedCurrency: String,
                             bonusValue: String)

  fun finish(data: Bundle)

  fun close()

  fun acceptResult(uri: Uri)

  fun showToolbar()

  fun lockOrientation()
  fun unlockRotation()
}
