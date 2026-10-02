package world.taqwa.app.feature.onboarding

import world.taqwa.app.location.LocationPermission

/** What onboarding's location screen does once the system prompt has been answered. */
enum class LocationAnswer {
    /** Allowed: the phone's location is used and onboarding moves on. */
    CONTINUE,

    /**
     * Declined: the city search opens straight away. App Review asks that the alternative come
     * after the system prompt rather than beside it (guideline 5.1.1(iv)), and a prayer app with
     * no place has nothing to show, so a city is what a refusal leads to, not an empty Today.
     */
    CHOOSE_CITY,

    /**
     * Not answered yet: iOS stops waiting after a minute while the prompt may still be up. Nothing
     * moves, and Continue asks again.
     */
    STAY,
}

fun locationAnswer(permission: LocationPermission): LocationAnswer = when (permission) {
    LocationPermission.GRANTED -> LocationAnswer.CONTINUE
    LocationPermission.DENIED -> LocationAnswer.CHOOSE_CITY
    LocationPermission.NOT_REQUESTED -> LocationAnswer.STAY
}
