package com.devinescript.utils

import com.devinescript.controller.baseUrl
import kotlinx.html.*


fun FlowOrMetaDataOrPhrasingContent.jsScript(src: String) {
    script(type = ScriptType.textJavaScript, src) { }
}

data class DomainWithBranch(
    val domainId: Int,
    val domainTitle: String,
    val domainSubtitle: String,
    val domainThumbnail: String,
    val branchId: Int,
    val branchTitle: String,
    val branchSubtitle: String,
    val branchThumbnail: String,
    val branchHref: String,
    val domainHref: String,
    var difficultyLevel: DifficultyLevel,
)

data class DomainWithBranch2(
    val domainId: Int,
    val domainTitle: String,
    val domainSubtitle: String,
    val domainThumbnail: String,
    val branchId: Int,
    val branchTitle: String,
    val branchSubtitle: String,
    val branchThumbnail: String,
    val branchHref: String,
    val domainHref: String,
    var difficultyLevel: String,
)


fun BODY.displayDemonstration(sections: List<Section>) {
    div(classes = "container") {
        sections.forEach {
            when (it.section_type) {
                SectionTypeEnum.GENERAL_REFERENCE -> {
                    div(classes = "referenc") {
                        h3 { +"${it.heading}" }
                        p { unsafe { +it.content } }
                        a(href = it.reference_link) { +"${it.reference_title}" }
                    }
                }

                SectionTypeEnum.PARAGRAPH -> {
                    p { unsafe { +it.content } }
                }

                SectionTypeEnum.QURAN_REFERENCE -> {
                    div(classes = "referenc") {
                        h3 { +"${it.heading}" }
                        p { unsafe { +it.content } }
                        p { unsafe { +"${it.content_ar}" } }
                        a(href = it.reference_link) { +"${it.reference_title}" }
                    }

                }

                SectionTypeEnum.MORAL -> {
                    h1 { +it.content }
                }

                SectionTypeEnum.IMAGE -> {
                    img(src = baseUrl + it.content)
                    if (it.reference_link != null) {
                        br { }
                        a(href = it.reference_link) { +"${it.reference_title}" }
                    }
                }

            }
        }
    }
}