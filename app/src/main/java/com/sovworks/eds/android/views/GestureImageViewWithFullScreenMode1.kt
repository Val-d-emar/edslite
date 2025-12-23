package com.sovworks.eds.android.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet

class GestureImageViewWithFullScreenMode(context: Context, attr: AttributeSet?) :
    GestureImageView(
        context,
        attr
    ) // implements android.view.View.OnSystemUiVisibilityChangeListener
{
    /* @Override
       public void onSystemUiVisibilityChange(int visibility) 
       {
           // Detect when we go out of low-profile mode, to also go out
           // of full screen.  We only do this when the low profile mode
           // is changing from its last state, and turning off.
           int diff = _lastSystemUiVis ^ visibility;
           _lastSystemUiVis = visibility;
           if ((diff&SYSTEM_UI_FLAG_LOW_PROFILE) != 0 && (visibility&SYSTEM_UI_FLAG_LOW_PROFILE) == 0) 
               setNavVisibility(true);        
       }*/
    fun setFullscreenMode(activate: Boolean) {
        if (activate) {
            _isFullScreenMode = true
            setNavVisibility(false)
        } else {
            _isFullScreenMode = false
            val h = handler
            h?.removeCallbacks(_navHider)
            systemUiVisibility = 0
        }
    }

    @SuppressLint("InlinedApi")
    private fun setNavVisibility(visible: Boolean) {
        var newVis = (SYSTEM_UI_FLAG_LAYOUT_STABLE
                or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        if (!visible) newVis =
            newVis or (SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        val changed = newVis == systemUiVisibility

        // Unschedule any pending event to hide navigation if we are
        // changing the visibility, or making the UI visible.
        if (changed || visible) {
            val h = handler
            h?.removeCallbacks(_navHider)
        }

        // Set the new desired visibility.
        systemUiVisibility = newVis
    }

    override fun onTouchUp() {
        super.onTouchUp()
        if (_isFullScreenMode) {
            setNavVisibility(true)
            delayedFullScreen()
        }
    }

    private var _isFullScreenMode = false
    private val _navHider = Runnable { setNavVisibility(false) }


    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (_isFullScreenMode) {
            setNavVisibility(true)
            delayedFullScreen()
        }
    }

    private fun delayedFullScreen() {
        // When we become visible, we show our navigation elements briefly
        // before hiding them.
        handler.postDelayed(_navHider, 2000)
    }
}
