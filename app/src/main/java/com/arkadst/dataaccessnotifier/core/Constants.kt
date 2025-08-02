package com.arkadst.dataaccessnotifier.core

object Constants {
    const val LOGIN_URL = "https://www.eesti.ee/timur/oauth2/authorization/govsso?callback_url=https://www.eesti.ee/auth/callback&locale=et"
    const val SUCCESS_URL = "https://www.eesti.ee/auth/callback"
    const val ACTION_TRIGGER_LOGIN = "TRIGGER_LOGIN"

    const val JWT_EXTEND_URL = "https://www.eesti.ee/timur/jwt/extend-jwt-session"
    val DATA_TRACKER_API_URL = dataTrackerUrl()

    fun dataTrackerUrl(): String {
        val dataSystems = arrayOf("digiregistratuur","elamislubade_ja_toolubade_register","kinnistusraamat","kutseregister","maksukohustuslaste_register","infosusteem_polis","politsei_taktikalise_juhtimise_andmekogu","pollumajandusloomade_register","pollumajandustoetuste_ja_pollumassiivide_register","rahvastikuregister","retseptikeskus","sotsiaalkaitse_infosusteem","sotsiaalteenuste_ja_toetuste_register","tooinspektsiooni_tooelu_infosusteem","tootuskindlustuse_andmekogu")
        return dataSystems.joinToString(
            prefix = "https://www.eesti.ee/andmejalgija/api/v1/usages?",
            separator = "&"
        ) { "dataSystemCodes=$it" }
    }
}
