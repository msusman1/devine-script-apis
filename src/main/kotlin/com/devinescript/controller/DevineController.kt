package com.devinescript.controller

import com.devinescript.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.response.*
import kotlinx.coroutines.*
import kotlinx.html.*
import java.io.File


val baseUrl = "http://192.168.1.3:8181/"

class DevineController(call: ApplicationCall) : BaseController(call) {
    val scope = CoroutineScope(Dispatchers.IO)

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


    private fun getBranchTitleAndSubTitleTranslated(branchId: Int, lang: String): Pair<String, String> {
        val domainStr = File("src/main/resources/devine_script/domain/${lang}/get_all.json")
        val type = object : TypeToken<List<DomainWithBranch>>() {}.type
        val domainsWithBranch = Gson().fromJson<List<DomainWithBranch>>(domainStr.readText(), type)
        val domainWithBranch = domainsWithBranch.find { it.branchId == branchId }
        val branchTitle = domainWithBranch?.branchTitle ?: ""
        val branchSubtitle = domainWithBranch?.branchSubtitle ?: ""
        return branchTitle to branchSubtitle
    }
    /*
    * load english domain and translate for all langs
     */

    suspend fun justTranslateAndDisplayAllDomains() {
        val langs = listOf("zh", "hi", "es", "fr", "ru", "ja", "pt", "id", "bn", "ar", "ur", "de", "it", "ko", "tr")
        val domainsToTranslate = listOf(196, 197, 198, 199, 200)

        val getEngDomainsStr = File("src/main/resources/devine_script/domain/get_all.json")
        val type = object : TypeToken<List<DomainWithBranch>>() {}.type
        val engDomains = Gson().fromJson<List<DomainWithBranch>>(getEngDomainsStr.readText(), type)
            .filter { domainsToTranslate.contains(it.domainId) }
        val finalList = mutableMapOf<String, List<DomainWithBranch2>>()
        langs.forEach { lang ->
            val domainsDeferred: List<Deferred<DomainWithBranch2>> = engDomains.map { domainWithBranch ->
                scope.async {
                    getModel(
                        domainWithBranch = domainWithBranch,
                        lang = lang,
                        branchTitleSubtitle = getBranchTitleAndSubTitleTranslated(domainWithBranch.branchId, lang),
                    )
                }
            }
            val domains: List<DomainWithBranch2> = domainsDeferred.awaitAll()
            finalList.put(lang, domains)
        }
        respondSuccess(finalList)

    }

    private suspend fun getModel(
        domainWithBranch: DomainWithBranch,
        lang: String,
        branchTitleSubtitle: Pair<String, String>,
    ) = DomainWithBranch2(
        domainId = domainWithBranch.domainId,
        domainTitle = OkhttpUtils.translate(domainWithBranch.domainTitle, lang) ?: "",
        domainSubtitle = OkhttpUtils.translate(domainWithBranch.domainSubtitle, lang) ?: "",
        domainThumbnail = domainWithBranch.domainThumbnail,
        branchId = domainWithBranch.branchId,
        branchTitle = branchTitleSubtitle.first,
        branchSubtitle = branchTitleSubtitle.second,
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

        val allDomainFile = File("src/main/resources/devine_script/domain/get_all.json")

        val typeForDomainWithBranch = object : TypeToken<List<DomainWithBranch>>() {}.type
        val allDomains: List<DomainWithBranch> = Gson().fromJson(allDomainFile.readText(), typeForDomainWithBranch)
        val nextDomainToLoad: DomainWithBranch? = allDomains.filter { it.domainId > domainId }.firstOrNull()
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
                            "$baseUrl/parse?domain_id=${nextDomainToLoad.domainId}&href=${nextDomainToLoad.domainHref}"
                        val newWindowUrl = nextDomainToLoad.domainHref
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










