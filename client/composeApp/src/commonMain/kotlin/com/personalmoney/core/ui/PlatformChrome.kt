package com.personalmoney.core.ui

/**
 * Whether this platform wears the chrome of a website or the chrome of an app.
 *
 * The screens are the same either way; what differs is the frame around them, and the two
 * frames are not interchangeable. A phone has a bottom bar under the thumb and a floating
 * button over the list. A site has a header across the top: the brand on the left, the
 * sections beside it, and the account on the right - and below a certain width those
 * sections fold into a drawer rather than into a bottom bar, because that is the gesture a
 * browser has taught people to expect.
 *
 * Drawn from the platform rather than from the window size, because a tablet is not a
 * website: a wide Android window still wants the app's frame. Window size then decides
 * which shape of *that* frame is used - see the breakpoints in [com.personalmoney.navigation.PersonalMoneyNavHost].
 */
internal expect val webChrome: Boolean
