package com.asfoundation.wallet.ui.gamification

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.asf.wallet.R
import com.asfoundation.wallet.router.TransactionsRouter
import com.asfoundation.wallet.ui.BaseActivity

class RewardsLevelActivity : BaseActivity(), GamificationView {

  lateinit var menu: Menu

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_rewards_level)
    toolbar()
    val fragment = MyLevelFragment()
    // Display the fragment as the main content.
    supportFragmentManager.beginTransaction()
        .add(R.id.fragment_container, fragment)
        .addToBackStack(fragment.javaClass.simpleName)
        .commit()
  }

  override fun onBackPressed() {
    if (supportFragmentManager.backStackEntryCount == 1) {
      TransactionsRouter().open(this, true)
      finish()
      return
    }
    super.onBackPressed()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        TransactionsRouter().open(this, true)
        finish()
        return true
      }

      R.id.action_info -> {
        showHowItWorksView()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_info, menu)
    this.menu = menu
    return super.onCreateOptionsMenu(menu)
  }

  override fun closeHowItWorksView() {
    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

    if (supportFragmentManager.backStackEntryCount > 0) {
      val fragment = supportFragmentManager.findFragmentByTag(HowItWorksFragment.javaClass.simpleName)
      if (fragment != null && fragment.javaClass.name.equals(
              HowItWorksFragment.javaClass.name, false)) {
        supportFragmentManager.beginTransaction().remove(currentFragment).commit()
      }
      supportFragmentManager.popBackStackImmediate()
    }
  }

  override fun showHowItWorksView() {
    supportFragmentManager.beginTransaction()
        .add(R.id.fragment_container, HowItWorksFragment.newInstance())
        .addToBackStack(HowItWorksFragment.javaClass.simpleName)
        .commit()
  }

  override fun showHowItWorksButton() {
    menu.findItem(R.id.action_info).isVisible = true
  }
}