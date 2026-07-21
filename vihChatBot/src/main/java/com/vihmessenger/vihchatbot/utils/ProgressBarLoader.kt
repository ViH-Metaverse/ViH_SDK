package com.vihmessenger.vihchatbot.utils

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.vihmessenger.vihchatbot.R

class ProgressBarLoader : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.layout_blocking_loader)
        dialog.setCancelable(false) // Prevent dismiss on back press
        return dialog
    }

    companion object {
        private var instance: ProgressBarLoader? = null

        fun show(loader: ProgressBarLoader?, fragmentManager: androidx.fragment.app.FragmentManager) {
            if (loader == null || loader.isAdded) return
            instance = loader
            loader.show(fragmentManager, "ProgressBarLoader")
        }

        fun hide() {
            instance?.dismiss()
            instance = null
        }
    }
}