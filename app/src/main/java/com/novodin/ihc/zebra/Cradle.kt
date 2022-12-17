package com.novodin.ihc.zebra

import android.content.Context
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKManager.EMDKListener
import com.symbol.emdk.EMDKManager.FEATURE_TYPE
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.personalshopper.CradleException
import com.symbol.emdk.personalshopper.CradleLedFlashInfo
import com.symbol.emdk.personalshopper.CradleResults
import com.symbol.emdk.personalshopper.PersonalShopper


class Cradle(context: Context) : EMDKListener {
    private var emdkManager: EMDKManager? = null

    init {
        val results = EMDKManager.getEMDKManager(context, this)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS)
            throw error("EMDKManager object request failed")
    }

    fun unlock(): Boolean {
        val personalShopper =
            emdkManager!!.getInstance(FEATURE_TYPE.PERSONALSHOPPER) as PersonalShopper

        personalShopper.cradle

        try {
            if (!personalShopper.cradle.isEnabled) personalShopper.cradle.enable()
            val ledFlashInfo = CradleLedFlashInfo(100, 500, true)
            val result = personalShopper.cradle.unlock(30, ledFlashInfo) // 30 seconds

            return result == CradleResults.SUCCESS
        } catch (e: CradleException) {
            //e.printStackTrace()
        }

        return false

    }

    override fun onOpened(emdkManager: EMDKManager?) {
        this.emdkManager = emdkManager
    }

    override fun onClosed() {
        if (emdkManager != null) {
            emdkManager!!.release()
            emdkManager = null
        }
    }
}