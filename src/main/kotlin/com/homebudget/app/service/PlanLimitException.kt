package com.homebudget.app.service

/**
 * The request is fine; the plan does not cover it.
 *
 * Its own type rather than [IllegalArgumentException] because the two mean different
 * things to whoever is on the other end: one says "you sent something wrong", this one
 * says "send it again once you have subscribed", and only the second one should ever put
 * a paywall in front of somebody.
 */
class PlanLimitException(message: String) : RuntimeException(message)
