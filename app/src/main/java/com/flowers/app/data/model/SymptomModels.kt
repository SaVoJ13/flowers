package com.flowers.app.data.model

data class SymptomTreeRoot(
    val version: String,
    val disclaimer_uk: String,
    val categories: List<SymptomCategory>
)

data class SymptomCategory(
    val id: String,
    val name_uk: String,
    val symptoms: List<SymptomNode>
)

data class SymptomNode(
    val id: String,
    val name_uk: String,
    val context_check: String? = null,
    val branch_recent_watering_le_3_days: SymptomBranch? = null,
    val branch_old_watering_gt_7_days: SymptomBranch? = null,
    val probable_cause_uk: String? = null,
    val explanation_uk: String? = null,
    val action_plan_uk: List<String>? = null
)

data class SymptomBranch(
    val condition_uk: String,
    val probable_cause_uk: String,
    val explanation_uk: String,
    val action_plan_uk: List<String>
)

data class DiagnosisResult(
    val probableCause: String,
    val explanation: String,
    val actionPlan: List<String>,
    val contextNote: String? = null
)
