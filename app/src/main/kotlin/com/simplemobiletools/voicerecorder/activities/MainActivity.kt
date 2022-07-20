package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.voicerecorder.BuildConfig
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.adapters.ViewPagerAdapter
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.STOP_AMPLITUDE_UPDATE
import com.simplemobiletools.voicerecorder.services.RecorderService
import kotlinx.android.synthetic.main.activity_main.*
import me.grantland.widget.AutofitHelper

class MainActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (checkAppSideloading()) {
            return
        }

        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                tryInitVoiceRecorder()
            } else {
                toast(R.string.no_audio_permissions)
                finish()
            }
        }

        if (config.recordAfterLaunch && !RecorderService.isRunning) {
            Intent(this@MainActivity, RecorderService::class.java).apply {
                try {
                    startService(this)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getPagerAdapter()?.onResume()
        setupTabColors()
    }

    override fun onPause() {
        super.onPause()
        config.lastUsedViewPagerPage = view_pager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        getPagerAdapter()?.onDestroy()

        Intent(this@MainActivity, RecorderService::class.java).apply {
            action = STOP_AMPLITUDE_UPDATE
            try {
                startService(this)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun tryInitVoiceRecorder() {
        if (isRPlus()) {
            setupViewPager()
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    setupViewPager()
                } else {
                    finish()
                }
            }
        }
    }

    private fun setupViewPager() {
        main_tabs_holder.removeAllTabs()
        val tabDrawables = arrayOf(R.drawable.ic_microphone_vector, R.drawable.ic_headset_vector)
        val tabLabels = arrayOf(R.string.recorder, R.string.player)

        tabDrawables.forEachIndexed { i, drawableId ->
            main_tabs_holder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getDrawable(drawableId))
                customView?.findViewById<TextView>(R.id.tab_item_label)?.setText(tabLabels[i])
                AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                main_tabs_holder.addTab(this)
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                view_pager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        view_pager.adapter = ViewPagerAdapter(this)
        view_pager.onPageChangeListener {
            main_tabs_holder.getTabAt(it)?.select()
            (view_pager.adapter as ViewPagerAdapter).finishActMode()
        }

        view_pager.currentItem = config.lastUsedViewPagerPage
        main_tabs_holder.getTabAt(config.lastUsedViewPagerPage)?.select()
    }

    private fun setupTabColors() {
        val activeView = main_tabs_holder.getTabAt(view_pager.currentItem)?.customView
        val inactiveView = main_tabs_holder.getTabAt(getInactiveTabIndex())?.customView
        updateBottomTabItemColors(activeView, true)
        updateBottomTabItemColors(inactiveView, false)

        main_tabs_holder.getTabAt(view_pager.currentItem)?.select()
        val bottomBarColor = getBottomTabsBackgroundColor()
        main_tabs_holder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    private fun getInactiveTabIndex() = if (view_pager.currentItem == 0) 1 else 0

    private fun getPagerAdapter() = (view_pager.adapter as? ViewPagerAdapter)

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_AUDIO_RECORD_VIEW or LICENSE_ANDROID_LAME or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
