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

//private val baseUrl = "http://192.168.10.97:8080/"
private val baseUrl = "http://192.168.50.143:8080/"

class DevineController(call: ApplicationCall) : BaseController(call) {
    val allImagesName = File("src/main/resources/devine_script/images").listFiles().map { it.name }

    fun getImageFor(suffix: String, id: Int): String {
        return allImagesName.find { it.startsWith(suffix + "_" + id) } ?: ""
    }

    suspend fun downlaodImages() {
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

    suspend fun transalateSingleDomain() {
        val langs = listOf("zh", "hi", "es", "fr", "ru", "ja", "pt", "id", "bn", "ar", "ur", "de", "it", "ko", "tr")
        val fileToRead = File("src/main/resources/devine_script/domainSections/185.json")
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
            val fileToWrite = File("src/main/resources/devine_script/domainSections/$lang/185.json")
            fileToWrite.writeText(Gson().toJson(newList))
            langsSectionMap[lang] = sections.size
        }
        call.respond("Langs Written: $langsSectionMap")

    }

    suspend fun translateSection() {
        val lang = call.request.queryParameters["lang"] ?: error("Provide lang")
//        val domainId = call.request.queryParameters["domain_id"] ?: error("Provide domain_id")
        val langDir = File("src/main/resources/devine_script/domainSections/$lang")
        if (!langDir.exists()) {
            langDir.mkdir()
        }
        var filesWirtten = 0
        for (domainId in 1..182) {
            val fileToRead = File("src/main/resources/devine_script/domainSections/$domainId.json")
            val fileToWrite = File(langDir, "$domainId.json")
            val readText = fileToRead.readText()
            val type = object : TypeToken<List<Section>>() {}.type
            val sections = Gson().fromJson<List<Section>>(readText, type)

            val newList = mutableListOf<Deferred<Section>>()

            sections.forEach { section ->
                val def = GlobalScope.async {
                    getTranalatedSectionModel(section, lang)
                }
                newList.add(def)
            }
            val resp2 = newList.awaitAll()
            fileToWrite.writeText(Gson().toJson(resp2))
            filesWirtten++
        }

        call.respond("Files Written: $filesWirtten")
    }


    private suspend fun getTranalatedSectionModel(
        section: Section,
        lang: String,

        ): Section {
        val contentTranslation = if (section.section_type == SectionTypeEnum.IMAGE) {
            section.content
        } else {
            OkhttpUtils.translate(section.content, lang) ?: ""
        }
        val headingTranslation =
            if (section.section_type == SectionTypeEnum.GENERAL_REFERENCE && section.heading != null) {
                OkhttpUtils.translate(section.heading, lang) ?: ""
            } else {
                section.heading
            }
        val refTitleTranslation =
            if ((section.section_type == SectionTypeEnum.GENERAL_REFERENCE || section.section_type == SectionTypeEnum.QURAN_REFERENCE) && section.reference_title != null) {
                OkhttpUtils.translate(section.reference_title, lang) ?: ""
            } else {
                section.reference_title
            }


        val mod = Section(
            section_type = section.section_type,
            content = contentTranslation,
            content_ar = section.content_ar,
            heading = headingTranslation,
            reference_title = refTitleTranslation,
            reference_link = section.reference_link,
        )
        return mod
    }

    suspend fun translate2() {
        val lang = call.request.queryParameters["lang"] ?: error("Provide lang")

        val getAllFile = File("src/main/resources/devine_script/domain/get_all.json")
        val type = object : TypeToken<List<DomainWithBranch>>() {}.type
        val resp = Gson().fromJson<List<DomainWithBranch>>(getAllFile.readText(), type)

        val newList = mutableListOf<Deferred<DomainWithBranch2>>()

        resp.forEach { domainWithBranch ->
            val def = GlobalScope.async {
                getMOdel(domainWithBranch, lang)
            }
            newList.add(def)
        }
        val resp2 = newList.awaitAll()
        call.respond(resp2)
    }

    private suspend fun getMOdel(
        domainWithBranch: DomainWithBranch,
        lang: String,

        ): DomainWithBranch2 {
        val mod = DomainWithBranch2(
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
        return mod
    }

    suspend fun translate() {
        val lang = call.request.queryParameters["lang"] ?: error("Provide lang")
        val query = call.request.queryParameters["q"] ?: error("Provide query")
        val translated: String? = OkhttpUtils.translate(query, lang)
        respondSuccess(translated)


    }

    suspend fun parse() {
        val getAllFile = File("src/main/resources/devine_script/domain/get_all_old.json")
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
            val allDomainFile = File("src/main/resources/devine_script/domain/get_all_old.json")
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

    suspend fun fetchAndJustDisplayDemonstration() {
        val href = call.request.queryParameters["href"]?.toString()
        val domainId = call.request.queryParameters["domain_id"]?.toIntOrNull()
        if (href == null || domainId == null) {
            respondError("Provide href and domain id")
            return
        }
        val demonstration: Demonstration? = DevineUtils.parseDemonstrations(href)
//        File("src/main/resources/devine_script/domainSections/${domainId}.json").writeText(Gson().toJson(demonstration))
        respondSuccess(demonstration)
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
                        img(src = "$baseUrl" + it.content)
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
        val questionPojoList = DevineUtils.parseAllDomains()
        respondSuccess(questionPojoList)
    }
}










