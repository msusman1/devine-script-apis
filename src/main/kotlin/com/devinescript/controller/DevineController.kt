package com.devinescript.controller

import com.devinescript.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.response.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.html.*
import java.io.File


val baseUrl = "http://192.168.1.3:8080/"

class DevineController(call: ApplicationCall) : BaseController(call) {

    /*
   * load all domains for english
   * list all files,
   * for each file download images and
   * update the image links from https to relative
    */
    suspend fun downloadImages() {
        val demonstrationFiles =
            File("src/main/resources/devine_script/domainSections").listFiles()?.toList()
        val type = object : TypeToken<List<Section>>() {}.type

        val fileChanged = mutableMapOf<String, Int>()
        demonstrationFiles?.forEach { file ->
            val sections = Gson().fromJson<List<Section>>(file.readText(), type)
            var sectionNo = 1
            var changed = false
            sections.forEach {
                if (it.section_type == SectionTypeEnum.IMAGE && it.content.startsWith("https")) {
                    it.content = OkhttpUtils.downloadFile(
                        it.content,
                        "domain_${file.nameWithoutExtension}_section_$sectionNo"
                    ) ?: ""
                    sectionNo++
                    changed = true
                }

            }
            if (changed) {
                fileChanged[file.name] = sectionNo - 1
                file.writeText(Gson().toJson(sections))
            }

        }
        respondSuccess("$fileChanged")
    }

    /*
    * load english domain section by domain id
    * and translate all sections and save in corresponding files by lang
     */
    suspend fun translateDomainSections() {

        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull()
        if (domainId == null) {
            respondError("Provide domain id")
            return
        }
        val langs = listOf("zh", "hi", "es", "fr", "ru", "ja", "pt", "id", "bn", "ar", "ur", "de", "it", "ko", "tr")
        val fileToRead = File("src/main/resources/devine_script/domainSections/$domainId.json")
        val readText = fileToRead.readText()
        val type = object : TypeToken<List<Section>>() {}.type
        val sections = Gson().fromJson<List<Section>>(readText, type)

        val langsSectionMap = mutableMapOf<String, Int>()
        langs.forEach { lang ->
            val newList = mutableListOf<Section>()
            sections.forEach { section ->
                val def: Section = getTranalatedSectionModel(section, lang)
                newList.add(def)
            }
            val fileToWrite = File("src/main/resources/devine_script/domainSections/$lang/$domainId.json")
            fileToWrite.writeText(Gson().toJson(newList))
            langsSectionMap[lang] = sections.size
        }
        call.respond("Langs Written: $langsSectionMap")

    }


    /*
    * load english domain and translate for all langs
     */
    suspend fun justTranslateAndDisplayAllDomains() {
        val lang = call.request.queryParameters["lang"] ?: error("Provide lang")

        val getAllFile = File("src/main/resources/devine_script/domain/get_all.json")
        val type = object : TypeToken<List<DomainWithBranch>>() {}.type
        val resp = Gson().fromJson<List<DomainWithBranch>>(getAllFile.readText(), type)

        val newList = mutableListOf<Deferred<DomainWithBranch2>>()

        resp.forEach { domainWithBranch ->
            val def = GlobalScope.async {
                getModel(domainWithBranch, lang)
            }
            newList.add(def)
        }
        val resp2 = newList.awaitAll()
        call.respond(resp2)
    }

    private suspend fun getModel(domainWithBranch: DomainWithBranch, lang: String) = DomainWithBranch2(
        domainId = domainWithBranch.domainId,
        domainTitle = OkhttpUtils.translate(domainWithBranch.domainTitle, lang) ?: "",
        domainSubtitle = OkhttpUtils.translate(domainWithBranch.domainSubtitle, lang) ?: "",
        domainThumbnail = domainWithBranch.domainThumbnail,
        branchId = domainWithBranch.branchId,
        branchTitle = OkhttpUtils.translate(domainWithBranch.branchTitle, lang) ?: "",
        branchSubtitle = OkhttpUtils.translate(domainWithBranch.branchSubtitle, lang) ?: "",
        branchThumbnail = domainWithBranch.branchThumbnail,
        branchHref = domainWithBranch.branchHref,
        domainHref = domainWithBranch.domainHref,
        difficultyLevel = domainWithBranch.difficultyLevel.translate(lang),
    )


    /*
    *   just display demonstrations for domain
     */
    suspend fun displayDemonstration() {
        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull() ?: error("Provide domain id")
        val allDomainSectionsFile = File("src/main/resources/devine_script/domainSections/$domainId.json")
        val allDomainFile = File("src/main/resources/devine_script/domain/get_all.json")
        val typeForDomainWithBranch = object : TypeToken<List<DomainWithBranch>>() {}.type
        val domainWithBranchList =
            Gson().fromJson<List<DomainWithBranch>>(allDomainFile.readText(), typeForDomainWithBranch)
        val nextDomainToLoad = domainWithBranchList.find { it.domainId == domainId + 1 }
        val sectionType = object : TypeToken<List<Section>>() {}.type
        val sections = Gson().fromJson<List<Section>>(allDomainSectionsFile.readText(), sectionType)
        val demonstration = Demonstration(DifficultyLevel.ADVANCED, "sdfsdf", sections)
        call.respondHtml {
            head {
                jsScript("/front-end/js/devine.js")
                styleLink("/front-end/css/devine.css")
            }

            body {
                displayDemonstration(demonstration.sections)
                if (nextDomainToLoad != null) {
                    val currentWindowUrl =
                        "$baseUrl/displayDomain?domain_id=${nextDomainToLoad.domainId}"
                    val newWindowUrl = nextDomainToLoad.domainHref
                    buttonInput {
                        value = "Next"
                        onClick = "onOkClick(\"$currentWindowUrl\",\"$newWindowUrl\")"
                    }
                }

            }


        }


    }

    /*
     * parse demonstration sections by domain href and save to corresponding domain section file for eng
     */
    suspend fun fetchDemonstration() {
        val href = call.request.queryParameters["href"]?.toString()
        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull()
        if (href == null || domainId == null) {
            respondError("Provide href and domain id")
            return
        }

        val allDomainsStr = File("src/main/resources/devine_script/domains/get_all.json").readText()
        val allDomains = Gson().fromJson(allDomainsStr, BaseResponseDomain::class.java).data
        val nextDomainToLoad: DomainModel? = allDomains.filter { it.domainId > domainId }.firstOrNull()
        val demonstration = DevineUtils.parseDemonstrations(href)
        File("src/main/resources/devine_script/domainSections/${domainId}.json").writeText(Gson().toJson(demonstration))
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
                            "$baseUrl/parse?domain_id=${nextDomainToLoad.domainId}&href=${nextDomainToLoad.href}"
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


    suspend fun fetchBranches() {
        val questionPojoList = DevineUtils.parseBranches()
        respondSuccess(questionPojoList)
    }

    suspend fun fetchDomains() {
        val questionPojoList = DevineUtils.parseAllDomains()
        respondSuccess(questionPojoList)
    }
}










