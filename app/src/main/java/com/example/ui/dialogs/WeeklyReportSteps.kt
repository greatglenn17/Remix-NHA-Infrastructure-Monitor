package com.example.ui.dialogs

import com.example.util.FormValidation

// short fits all five tabs on a phone; label heads the step.
enum class WeeklyStep(val short: String, val label: String) {
    WEEK("Week", "Week and progress"),
    CREW("Crew", "Manpower and equipment"),
    WORK("Work", "Activities and issues"),
    PHOTOS("Photos", "Photos and documents"),
    REVIEW("Review", "Review and save")
}

fun weeklyErrors(accomplishments: Map<String, Pair<String, String>>, scopeNames: Collection<String>): List<Pair<WeeklyStep, String>> =
    accomplishments.filterKeys { it in scopeNames }.flatMap { (item, vals) ->
        listOfNotNull(
            FormValidation.percentError(vals.first)?.let { WeeklyStep.WEEK to "$item: target must be 0 to 100%" },
            FormValidation.percentError(vals.second)?.let { WeeklyStep.WEEK to "$item: actual must be 0 to 100%" }
        )
    }
