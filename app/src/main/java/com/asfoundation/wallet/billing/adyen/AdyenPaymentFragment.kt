package com.asfoundation.wallet.billing.adyen

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import com.adyen.checkout.base.model.payments.response.Action
import com.adyen.checkout.base.ui.view.RoundCornerImageView
import com.adyen.checkout.card.CardComponent
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.redirect.RedirectComponent
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.TextDelegate
import com.appcoins.wallet.bdsbilling.Billing
import com.appcoins.wallet.billing.repository.entity.TransactionData
import com.asf.wallet.BuildConfig
import com.asf.wallet.R
import com.asfoundation.wallet.billing.analytics.BillingAnalytics
import com.asfoundation.wallet.navigator.UriNavigator
import com.asfoundation.wallet.ui.iab.FragmentNavigator
import com.asfoundation.wallet.ui.iab.IabActivity
import com.asfoundation.wallet.ui.iab.IabView
import com.asfoundation.wallet.ui.iab.InAppPurchaseInteractor
import com.asfoundation.wallet.util.KeyboardUtils
import com.google.android.material.textfield.TextInputLayout
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.support.DaggerFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import kotlinx.android.synthetic.main.adyen_credit_card_layout.*
import kotlinx.android.synthetic.main.adyen_credit_card_layout.fragment_credit_card_authorization_progress_bar
import kotlinx.android.synthetic.main.adyen_credit_card_pre_selected.*
import kotlinx.android.synthetic.main.dialog_buy_buttons_payment_methods.*
import kotlinx.android.synthetic.main.fragment_iab_error.*
import kotlinx.android.synthetic.main.fragment_iab_error.view.*
import kotlinx.android.synthetic.main.fragment_iab_transaction_completed.*
import kotlinx.android.synthetic.main.selected_payment_method_cc.*
import kotlinx.android.synthetic.main.view_purchase_bonus.*
import org.apache.commons.lang3.StringUtils
import java.math.BigDecimal
import java.util.*
import javax.inject.Inject

class AdyenPaymentFragment : DaggerFragment(), AdyenPaymentView {

  @Inject
  lateinit var inAppPurchaseInteractor: InAppPurchaseInteractor
  @Inject
  lateinit var billing: Billing
  @Inject
  lateinit var analytics: BillingAnalytics
  @Inject
  lateinit var adyenPaymentInteractor: AdyenPaymentInteractor
  @Inject
  lateinit var adyenEnvironment: Environment
  private lateinit var iabView: IabView
  private lateinit var presenter: AdyenPaymentPresenter
  private lateinit var cardConfiguration: CardConfiguration
  private lateinit var compositeDisposable: CompositeDisposable
  private lateinit var redirectComponent: RedirectComponent
  private var backButton: PublishRelay<Boolean>? = null
  private var paymentDataSubject: ReplaySubject<AdyenCardWrapper>? = null
  private var paymentDetailsSubject: PublishSubject<RedirectComponentModel>? = null
  private lateinit var adyenCardNumberLayout: TextInputLayout
  private lateinit var adyenExpiryDateLayout: TextInputLayout
  private lateinit var adyenSecurityCodeLayout: TextInputLayout
  private var adyenCardImageLayout: RoundCornerImageView? = null
  private var adyenSaveDetailsSwitch: SwitchCompat? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    backButton = PublishRelay.create<Boolean>()
    paymentDataSubject = ReplaySubject.create<AdyenCardWrapper>()
    paymentDetailsSubject = PublishSubject.create<RedirectComponentModel>()
    val navigator = FragmentNavigator(activity as UriNavigator?, iabView)
    compositeDisposable = CompositeDisposable()
    presenter =
        AdyenPaymentPresenter(this, compositeDisposable, AndroidSchedulers.mainThread(),
            Schedulers.io(), RedirectComponent.getReturnUrl(context!!), analytics, domain, origin,
            adyenPaymentInteractor, inAppPurchaseInteractor.parseTransaction(transactionData, true),
            navigator, paymentType, transactionType, amount, currency, isPreSelected)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {
    return if (isPreSelected) {
      inflater.inflate(R.layout.adyen_credit_card_pre_selected, container,
          false)
    } else {
      inflater.inflate(R.layout.adyen_credit_card_layout, container, false)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupUi(view)
    presenter.present(savedInstanceState)
  }

  private fun setupUi(view: View) {
    setupAdyenLayouts()
    setupTransactionCompleteAnimation()
    handleBuyButtonText()
    if (paymentType == PaymentType.CARD.name) setupCardConfiguration()

    handlePreSelectedView(view)
    handleBonusAnimation()

    showProduct()
  }

  override fun finishCardConfiguration(
      paymentMethod: com.adyen.checkout.base.model.paymentmethods.PaymentMethod,
      isStored: Boolean, forget: Boolean, savedInstance: Bundle?) {

    buy_button.visibility = View.VISIBLE
    cancel_button.visibility = View.VISIBLE

    val color = ResourcesCompat.getColor(resources, R.color.btn_end_gradient_color, null)
    adyenCardNumberLayout.boxStrokeColor = color
    adyenExpiryDateLayout.boxStrokeColor = color
    adyenSecurityCodeLayout.boxStrokeColor = color
    handleLayoutVisibility(isStored)
    prepareCardComponent(paymentMethod, forget, savedInstance)
    setStoredPaymentInformation(isStored)
  }

  override fun retrievePaymentData(): Observable<AdyenCardWrapper> {
    return paymentDataSubject!!
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.apply {
      putString(CARD_NUMBER_KEY, adyenCardNumberLayout.editText?.text.toString())
      putString(EXPIRY_DATE_KEY, adyenExpiryDateLayout.editText?.text.toString())
      putString(CVV_KEY, adyenSecurityCodeLayout.editText?.text.toString())
      putBoolean(SAVE_DETAILS_KEY, adyenSaveDetailsSwitch?.isChecked ?: false)
    }
    presenter.onSaveInstanceState(outState)
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    check(context is IabView) { "adyen payment fragment must be attached to IAB activity" }
    iabView = context
  }

  override fun getAnimationDuration() = lottie_transaction_success.duration

  override fun showProduct() {
    val formatter = Formatter()
    try {
      app_icon?.setImageDrawable(context!!.packageManager
          .getApplicationIcon(domain))
      app_name?.text = getApplicationName(domain)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    app_sku_description?.text = arguments!!.getString(IabActivity.PRODUCT_NAME)
    val appcValue = formatter.format(Locale.getDefault(), "%(,.2f", appcAmount.toDouble())
        .toString() + " APPC"
    appc_price.text = appcValue
  }

  override fun showLoading() {
    fragment_credit_card_authorization_progress_bar.visibility = View.VISIBLE
    if (isPreSelected) {
      payment_methods?.visibility = View.INVISIBLE
    } else {
      adyen_card_form.visibility = View.INVISIBLE
      change_card_button.visibility = View.INVISIBLE
      cancel_button.visibility = View.INVISIBLE
      buy_button.visibility = View.INVISIBLE
    }
  }

  override fun hideLoadingAndShowView() {
    fragment_credit_card_authorization_progress_bar?.visibility = View.GONE
    if (isPreSelected) {
      payment_methods?.visibility = View.VISIBLE
    } else {
      adyen_card_form.visibility = View.VISIBLE
      cancel_button.visibility = View.VISIBLE
    }
  }

  override fun showNetworkError() {
    main_view?.visibility = View.GONE
    main_view_pre_selected?.visibility = View.GONE
    fragment_credit_card_authorization_progress_bar?.visibility = View.GONE
    fragment_iab_error?.visibility = View.VISIBLE
    fragment_iab_error?.activity_iab_error_message?.setText(R.string.notification_no_network_poa)
    fragment_iab_error_pre_selected?.visibility = View.VISIBLE
    fragment_iab_error_pre_selected?.activity_iab_error_message?.setText(
        R.string.notification_no_network_poa)
  }

  override fun backEvent(): Observable<Any> {
    return RxView.clicks(cancel_button)
        .mergeWith(backButton)
  }

  override fun showSuccess() {
    iab_activity_transaction_completed.visibility = View.VISIBLE
    fragment_credit_card_authorization_progress_bar?.visibility = View.GONE
    if (isPreSelected) {
      main_view?.visibility = View.GONE
      main_view_pre_selected?.visibility = View.GONE
    } else {
      fragment_credit_card_authorization_progress_bar.visibility = View.GONE
      credit_card_info.visibility = View.GONE
      lottie_transaction_success.visibility = View.VISIBLE
      fragment_iab_error?.visibility = View.GONE
      fragment_iab_error_pre_selected?.visibility = View.GONE
    }
  }

  override fun showGenericError() {
    main_view?.visibility = View.GONE
    main_view_pre_selected?.visibility = View.GONE
    fragment_iab_error?.visibility = View.VISIBLE
    fragment_credit_card_authorization_progress_bar?.visibility = View.GONE
    fragment_iab_error?.activity_iab_error_message?.setText(R.string.unknown_error)
    fragment_iab_error_pre_selected?.visibility = View.VISIBLE
    fragment_iab_error_pre_selected?.activity_iab_error_message?.setText(
        R.string.unknown_error)
  }

  override fun showSpecificError(refusalCode: Int) {
    main_view?.visibility = View.GONE
    main_view_pre_selected?.visibility = View.GONE
    var message = getString(R.string.notification_payment_refused)

    when (refusalCode) {
      8, 24 -> message =
          getString(R.string.notification_payment_refused) //To be changed on errors ticket
    }

    fragment_credit_card_authorization_progress_bar?.visibility = View.GONE
    fragment_iab_error?.activity_iab_error_message?.text = message
    fragment_iab_error_pre_selected?.activity_iab_error_message?.text = message
    fragment_iab_error?.visibility = View.VISIBLE
    fragment_iab_error_pre_selected?.visibility = View.VISIBLE
  }

  override fun getMorePaymentMethodsClicks() = RxView.clicks(more_payment_methods)

  override fun showMoreMethods() {
    main_view?.let { KeyboardUtils.hideKeyboard(it) }
    main_view_pre_selected?.let { KeyboardUtils.hideKeyboard(it) }
    iabView.unlockRotation()
    iabView.showPaymentMethodsView()
  }

  override fun setRedirectComponent(action: Action, uid: String) {
    redirectComponent = RedirectComponent.PROVIDER.get(this)
    redirectComponent.observe(this, Observer {
      paymentDetailsSubject?.onNext(RedirectComponentModel(uid, it.details!!, it.paymentData))
    })
  }

  override fun forgetCardClick(): Observable<Any> {
    return if (change_card_button != null) RxView.clicks(change_card_button)
    else RxView.clicks(change_card_button_pre_selected)
  }

  override fun showProductPrice(fiatAmount: BigDecimal, currencyCode: String) {
    val fiatPrice = Formatter().format(Locale.getDefault(), "%(,.2f", fiatAmount.toDouble())
    var fiatText = "$fiatPrice $currencyCode"

    frequency?.let {
      fiatText = "$fiatText/$frequency"
      val oldPrice = appc_price.text.toString()

      val formatter = Formatter()
      val appcText = formatter.format(Locale.getDefault(), "~%s", oldPrice)
          .toString()
      appc_price.text = appcText
    }


    fiat_price.text = fiatText
  }

  override fun errorDismisses() = RxView.clicks(activity_iab_error_ok_button)

  override fun buyButtonClicked() = RxView.clicks(buy_button)

  override fun close(bundle: Bundle?) = iabView.close(bundle)

  override fun submitUriResult(uri: Uri) = redirectComponent.handleRedirectResponse(uri)

  override fun getPaymentDetails(): Observable<RedirectComponentModel> = paymentDetailsSubject!!

  override fun lockRotation() = iabView.lockRotation()

  override fun hideKeyboard() {
    view?.let { KeyboardUtils.hideKeyboard(view) }
  }

  private fun setBackListener(view: View) {
    iabView.disableBack()
    view.isFocusableInTouchMode = true
    view.requestFocus()
    view.setOnKeyListener { _: View?, _: Int, keyEvent: KeyEvent ->
      if (keyEvent.action == KeyEvent.ACTION_DOWN
          && keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
        backButton?.accept(true)
      }
      true
    }
  }

  private fun setupAdyenLayouts() {
    adyenCardNumberLayout =
        adyen_card_form_pre_selected?.findViewById(R.id.textInputLayout_cardNumber)
            ?: adyen_card_form.findViewById(R.id.textInputLayout_cardNumber)
    adyenExpiryDateLayout =
        adyen_card_form_pre_selected?.findViewById(R.id.textInputLayout_expiryDate)
            ?: adyen_card_form.findViewById(R.id.textInputLayout_expiryDate)
    adyenSecurityCodeLayout =
        adyen_card_form_pre_selected?.findViewById(R.id.textInputLayout_securityCode)
            ?: adyen_card_form.findViewById(R.id.textInputLayout_securityCode)
    adyenCardImageLayout = adyen_card_form_pre_selected?.findViewById(R.id.cardBrandLogo_imageView)
        ?: adyen_card_form?.findViewById(R.id.cardBrandLogo_imageView)
    adyenSaveDetailsSwitch =
        adyen_card_form_pre_selected?.findViewById(R.id.switch_storePaymentMethod)
            ?: adyen_card_form?.findViewById(R.id.switch_storePaymentMethod)
  }

  private fun setupCardConfiguration() {
    val cardConfigurationBuilder =
        CardConfiguration.Builder(activity as Context, BuildConfig.ADYEN_PUBLIC_KEY)

    cardConfiguration = cardConfigurationBuilder.let {
      it.setEnvironment(adyenEnvironment)
      it.build()
    }
  }

  @Throws(PackageManager.NameNotFoundException::class)
  private fun getApplicationName(appPackage: String): CharSequence? {
    val packageManager = context!!.packageManager
    val packageInfo =
        packageManager.getApplicationInfo(appPackage, 0)
    return packageManager.getApplicationLabel(packageInfo)
  }

  private fun setupTransactionCompleteAnimation() {
    val textDelegate = TextDelegate(lottie_transaction_success)
    textDelegate.setText("bonus_value", bonus)
    textDelegate.setText("bonus_received",
        resources.getString(R.string.gamification_purchase_completed_bonus_received))
    lottie_transaction_success.setTextDelegate(textDelegate)
    lottie_transaction_success.setFontAssetDelegate(object : FontAssetDelegate() {
      override fun fetchFont(fontFamily: String): Typeface {
        return Typeface.create("sans-serif-medium", Typeface.BOLD)
      }
    })
  }

  private fun showBonus() {
    bonus_layout.visibility = View.VISIBLE
    bonus_msg.visibility = View.VISIBLE
    bonus_value.text = getString(R.string.gamification_purchase_header_part_2, bonus)
    frequency?.let {
      bonus_msg.text = "You will receive this bonus for each payment"
    }
  }

  private fun handleLayoutVisibility(isStored: Boolean) {
    if (isStored) {
      adyenCardNumberLayout.visibility = View.GONE
      adyenExpiryDateLayout.visibility = View.GONE
      adyenCardImageLayout?.visibility = View.GONE
      change_card_button?.visibility = View.VISIBLE
      change_card_button_pre_selected?.visibility = View.VISIBLE
      view?.let { KeyboardUtils.showKeyboard(it) }
    } else {
      adyenCardNumberLayout.visibility = View.VISIBLE
      adyenExpiryDateLayout.visibility = View.VISIBLE
      adyenCardImageLayout?.visibility = View.VISIBLE
      change_card_button?.visibility = View.GONE
      change_card_button_pre_selected?.visibility = View.GONE
    }

  }

  private fun prepareCardComponent(
      paymentMethod: com.adyen.checkout.base.model.paymentmethods.PaymentMethod, forget: Boolean,
      savedInstanceState: Bundle?) {
    if (forget) viewModelStore.clear()
    val cardComponent =
        CardComponent.PROVIDER.get(this, paymentMethod, cardConfiguration)
    if (forget) clearFields()
    adyen_card_form_pre_selected?.attach(cardComponent, this)
    cardComponent.observe(this, androidx.lifecycle.Observer {
      if (it != null && it.isValid) {
        buy_button?.isEnabled = true
        view?.let { view -> KeyboardUtils.hideKeyboard(view) }
        it.data.paymentMethod?.let { paymentMethod ->
          paymentDataSubject?.onNext(
              AdyenCardWrapper(paymentMethod, adyenSaveDetailsSwitch?.isChecked ?: false))
        }
      } else {
        buy_button?.isEnabled = false
      }
    })
    if (!forget) {
      getFieldValues(savedInstanceState)
    }
  }

  private fun getFieldValues(savedInstanceState: Bundle?) {
    savedInstanceState?.let {
      adyenCardNumberLayout.editText?.setText(it.getString(CARD_NUMBER_KEY, ""))
      adyenExpiryDateLayout.editText?.setText(it.getString(EXPIRY_DATE_KEY, ""))
      adyenSecurityCodeLayout.editText?.setText(it.getString(CVV_KEY, ""))
      adyenSaveDetailsSwitch?.isChecked = it.getBoolean(SAVE_DETAILS_KEY, false)
      it.clear()
    }
  }

  private fun setStoredPaymentInformation(isStored: Boolean) {
    if (isStored) {
      adyen_card_form_pre_selected_number?.text =
          adyenCardNumberLayout.editText?.text
      adyen_card_form_pre_selected_number?.visibility = View.VISIBLE
      payment_method_ic?.setImageDrawable(adyenCardImageLayout?.drawable)
    } else {
      adyen_card_form_pre_selected_number?.visibility = View.GONE
      payment_method_ic?.visibility = View.GONE
    }
  }

  private fun clearFields() {
    adyenCardNumberLayout.editText?.text = null
    adyenCardNumberLayout.editText?.isEnabled = true
    adyenExpiryDateLayout.editText?.text = null
    adyenExpiryDateLayout.editText?.isEnabled = true
    adyenSecurityCodeLayout.editText?.text = null
    adyenCardNumberLayout.requestFocus()
    adyenSecurityCodeLayout.error = null
  }

  private fun handleBonusAnimation() {
    if (StringUtils.isNotBlank(bonus)) {
      lottie_transaction_success.setAnimation(R.raw.transaction_complete_bonus_animation)
      setupTransactionCompleteAnimation()
    } else {
      lottie_transaction_success.setAnimation(R.raw.success_animation)
    }
  }

  private fun handlePreSelectedView(view: View) {
    if (isPreSelected) {
      showBonus()
    } else {
      cancel_button.setText(R.string.back_button)
      setBackListener(view)
    }
  }

  private fun handleBuyButtonText() {
    when {
      transactionType.equals(TransactionData.TransactionType.DONATION.name, ignoreCase = true) -> {
        buy_button.setText(R.string.action_donate)
      }
      frequency != null -> {
        buy_button.text = "Subscribe"
      }
      else -> {
        buy_button.setText(R.string.action_buy)
      }
    }
  }

  override fun onDestroyView() {
    iabView.enableBack()
    presenter.stop()
    super.onDestroyView()
  }

  override fun onDestroy() {
    backButton = null
    paymentDataSubject = null
    paymentDetailsSubject = null
    super.onDestroy()
  }

  companion object {

    private const val TRANSACTION_TYPE_KEY = "type"
    private const val PAYMENT_TYPE_KEY = "payment_type"
    private const val DOMAIN_KEY = "domain"
    private const val ORIGIN_KEY = "origin"
    private const val TRANSACTION_DATA_KEY = "transaction_data"
    private const val APPC_AMOUNT_KEY = "appc_amount"
    private const val AMOUNT_KEY = "amount"
    private const val CURRENCY_KEY = "currency"
    private const val BONUS_KEY = "bonus"
    private const val PRE_SELECTED_KEY = "pre_selected"
    private const val FREQUENCY = "frequency"
    private const val CARD_NUMBER_KEY = "card_number"
    private const val EXPIRY_DATE_KEY = "expiry_date"
    private const val CVV_KEY = "cvv_key"
    private const val SAVE_DETAILS_KEY = "save_details"

    @JvmStatic
    fun newInstance(transactionType: String, paymentType: PaymentType, domain: String,
                    origin: String?, transactionData: String?, appcAmount: BigDecimal,
                    amount: BigDecimal, currency: String?, bonus: String?,
                    isPreSelected: Boolean, frequency: String?): AdyenPaymentFragment {
      val fragment = AdyenPaymentFragment()
      val bundle = Bundle()
      bundle.apply {
        putString(TRANSACTION_TYPE_KEY, transactionType)
        putString(PAYMENT_TYPE_KEY, paymentType.name)
        putString(DOMAIN_KEY, domain)
        putString(ORIGIN_KEY, origin)
        putString(TRANSACTION_DATA_KEY, transactionData)
        putSerializable(APPC_AMOUNT_KEY, appcAmount)
        putSerializable(AMOUNT_KEY, amount)
        putString(CURRENCY_KEY, currency)
        putString(BONUS_KEY, bonus)
        putBoolean(PRE_SELECTED_KEY, isPreSelected)
        putString(FREQUENCY, frequency)
        fragment.arguments = this
      }
      return fragment
    }
  }

  private val transactionType: String by lazy {
    if (arguments!!.containsKey(TRANSACTION_TYPE_KEY)) {
      arguments!!.getString(TRANSACTION_TYPE_KEY)
    } else {
      throw IllegalArgumentException("transaction type data not found")
    }
  }

  private val paymentType: String by lazy {
    if (arguments!!.containsKey(PAYMENT_TYPE_KEY)) {
      arguments!!.getString(PAYMENT_TYPE_KEY)
    } else {
      throw IllegalArgumentException("payment type data not found")
    }
  }

  private val domain: String by lazy {
    if (arguments!!.containsKey(DOMAIN_KEY)) {
      arguments!!.getString(DOMAIN_KEY)
    } else {
      throw IllegalArgumentException("domain data not found")
    }
  }

  private val origin: String? by lazy {
    if (arguments!!.containsKey(ORIGIN_KEY)) {
      arguments!!.getString(ORIGIN_KEY)
    } else {
      throw IllegalArgumentException("origin not found")
    }
  }

  private val transactionData: String by lazy {
    if (arguments!!.containsKey(TRANSACTION_DATA_KEY)) {
      arguments!!.getString(TRANSACTION_DATA_KEY)
    } else {
      throw IllegalArgumentException("transaction data not found")
    }
  }

  private val appcAmount: BigDecimal by lazy {
    if (arguments!!.containsKey(APPC_AMOUNT_KEY)) {
      arguments!!.getSerializable(APPC_AMOUNT_KEY) as BigDecimal
    } else {
      throw IllegalArgumentException("appc amount data not found")
    }
  }

  private val amount: BigDecimal by lazy {
    if (arguments!!.containsKey(AMOUNT_KEY)) {
      arguments!!.getSerializable(AMOUNT_KEY) as BigDecimal
    } else {
      throw IllegalArgumentException("amount data not found")
    }
  }

  private val currency: String by lazy {
    if (arguments!!.containsKey(CURRENCY_KEY)) {
      arguments!!.getString(CURRENCY_KEY)
    } else {
      throw IllegalArgumentException("currency data not found")
    }
  }

  private val bonus: String by lazy {
    if (arguments!!.containsKey(BONUS_KEY)) {
      arguments!!.getString(BONUS_KEY)
    } else {
      throw IllegalArgumentException("bonus data not found")
    }
  }

  private val isPreSelected: Boolean by lazy {
    if (arguments!!.containsKey(PRE_SELECTED_KEY)) {
      arguments!!.getBoolean(PRE_SELECTED_KEY)
    } else {
      throw IllegalArgumentException("pre selected data not found")
    }
  }

  private val frequency: String? by lazy {
    if (arguments!!.containsKey(FREQUENCY)) {
      arguments!!.getString(FREQUENCY)
    } else {
      null
    }
  }
}