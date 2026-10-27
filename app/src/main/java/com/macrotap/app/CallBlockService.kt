package com.macrotap.app

import android.telecom.Call
import android.telecom.CallScreeningService

class CallBlockService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val r = CallScreeningService.CallResponse.Builder()
        if (Game.callsBlocked(this) &&
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        ) {
            r.setDisallowCall(true)
            r.setRejectCall(true)
            r.setSkipNotification(true)
        }
        respondToCall(callDetails, r.build())
    }
}
