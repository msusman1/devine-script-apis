package com.devinescript.controller

import com.devinescript.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.application.*
import io.ktor.html.*
import kotlinx.html.*
import java.io.File

fun FlowOrMetaDataOrPhrasingContent.jsScript(src: String) {
    script(type = ScriptType.textJavaScript, src) { }
}

data class RootResponseDomainWithBranch(val data: List<DomainWithBranch>, val success: Boolean)
data class RootResponseDemonstration(val data: Demonstration, val success: Boolean)
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

class DevineController(call: ApplicationCall) : BaseController(call) {
    suspend fun parse() {
        val getAllFile = File("src/main/resources/devine_script/domain/get_all.json")
        val type = object : TypeToken<List<DomainWithBranch>>() {}.type
        val resp = Gson().fromJson<List<DomainWithBranch>>(getAllFile.readText(), type)
        val grouping: Map<Int, List<DomainWithBranch>> = resp.groupBy { it.branchId }
        grouping.forEach { t, u: List<DomainWithBranch> ->
            val file = File("src/main/resources/devine_script/domain/$t.json")
            file.writeText(Gson().toJson(u))
        }
        respondSuccess(grouping.size)
    }

    suspend fun renameFiles() {
        val allDomains: Array<File>? = File("src/main/resources/devine_script/demonstration").listFiles()
        var fileRenamed = 0
        allDomains?.forEach { file ->
            if (!file.name.contains("json")) {
                file.copyTo(File("src/main/resources/devine_script/demonstration/${file.name}.json"))
                file.delete()
                fileRenamed++
            }

        }
        respondSuccess(msg = "renamed:$fileRenamed")
    }

    suspend fun displayDemonstration() {
        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull() ?: error("Provide domain id")
        val allDomainSectionsFile = File("src/main/resources/devine_script/domainSections/$domainId.json")
        val nextDomainToLoad = if (domainId < 182) {
            val allDomainFile = File("src/main/resources/devine_script/domain/get_all.json")
            val typeForDomainWithBranch = object : TypeToken<List<DomainWithBranch>>() {}.type
            val domainWithBranchList =
                Gson().fromJson<List<DomainWithBranch>>(allDomainFile.readText(), typeForDomainWithBranch)
            domainWithBranchList.find { it.domainId == domainId + 1 }
        } else {
            null
        }


        val sectionType = object : TypeToken<List<Section>>() {}.type
        val sections = Gson().fromJson<List<Section>>(allDomainSectionsFile.readText(), sectionType)
        val demonstration = Demonstration(DifficultyLevel.ADVANCED, "sdfsdf", sections)
        call.respondHtml {
            head {
                jsScript("/front-end/js/devine.js")
                styleLink("/front-end/css/devine.css")
            }

            body {
                displayDemonstration(demonstration)
                if (nextDomainToLoad != null) {
                    val currentWindowUrl =
                        "http://192.168.10.97:8080/displayDomain?domain_id=${nextDomainToLoad.domainId}"
                    val newWindowUrl = nextDomainToLoad.domainHref
                    buttonInput {
                        value = "Next"
                        onClick = "onOkClick(\"$currentWindowUrl\",\"$newWindowUrl\")"
                    }
                }

            }


        }


    }

    suspend fun fetchDemonstration() {
        val href = call.request.queryParameters["href"]?.toString()
        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull()
        if (href == null || domainId == null) {
            respondError("Provide href and domain id")
            return
        }
        val allDomainsStr = File("devine-script-data/domains/all.json").readText()
        val allDomains = Gson().fromJson(allDomainsStr, BaseResponseDomain::class.java).data
        val nextDomainToLoad: DomainModel? = allDomains.filter { it.domainId > domainId }.firstOrNull()
        val demonstration: Demonstration? = DevineUtils.parseDemonstrations(href)
        val resp = Response(true, data = demonstration)
        File("devine-script-data/sections/${domainId}.json").writeText(Gson().toJson(resp))
        if (demonstration != null) {
            call.respondHtml {
                head {
                    jsScript("/front-end/js/devine.js")
                    styleLink("/front-end/css/devine.css")
                }

                body {
                    displayDemonstration(demonstration)
                    if (nextDomainToLoad != null) {
                        val currentWindowUrl =
                            "http://192.168.10.97:8080/parse?domain_id=${nextDomainToLoad.domainId}&href=${nextDomainToLoad.href}"
                        val newWindowUrl = nextDomainToLoad.href
                        buttonInput {
                            value = "Next"
                            onClick = "onOkClick(\"$currentWindowUrl\",\"$newWindowUrl\")"
                        }
                    }

                }


            }
        } else {
            respondError("Something went wrong")
        }


    }

    private fun BODY.displayDemonstration(demonstration: Demonstration) {
        div(classes = "container") {
            demonstration.sections.forEach {
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
                        img(src = it.content)
                        if (it.reference_link != null) {
                            br { }
                            a(href = it.reference_link) { +"${it.reference_title}" }
                        }
                    }

                }
            }
        }
    }

    suspend fun fetchBranches() {
        val questionPojoList = DevineUtils.parseBranches()
        respondSuccess(questionPojoList)
    }

    suspend fun fetchDomains() {
        val questionPojoList = DevineUtils.parseDomain()
        respondSuccess(questionPojoList)
    }
}









