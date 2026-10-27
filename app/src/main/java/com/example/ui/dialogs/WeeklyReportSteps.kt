package com.example.ui.dialogs

import com.example.util.FormValidation

enum class WeeklyStep(val label: String) {
    WEEK("Week and progress"),
    CREW("Manpower and equipment"),
    WORK("Activities and issues"),
    PHOTOS("Photos and documents"),
    REVIEW("Review and save")
}

fun weeklyErrors(accomplishments: Map<String, Pair<String, String>>): List<Pair<WeeklyStep, String>> =
    accomplishments.flatMap { (item, vals) ->
        listOfNotNull(
            FormValidation.percentError(vals.first)?.let { WeeklyStep.WEEK to "$item: target $it" },
            FormValidation.percentError(vals.second)?.let { WeeklyStep.WEEK to "$item: actual $it" }
        )
    }
