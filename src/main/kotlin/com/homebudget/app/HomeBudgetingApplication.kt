package com.homebudget.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HomeBudgetingApplication

fun main(args: Array<String>) {
    runApplication<HomeBudgetingApplication>(*args)
}
