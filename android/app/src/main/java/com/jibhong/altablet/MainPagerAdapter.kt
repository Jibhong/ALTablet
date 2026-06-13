package com.jibhong.altablet

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jibhong.altablet.fragment.SettingsFragment
import com.jibhong.altablet.fragment.TabletFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TabletFragment()
            1 -> SettingsFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}