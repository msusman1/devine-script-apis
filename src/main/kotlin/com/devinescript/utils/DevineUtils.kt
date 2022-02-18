package com.devinescript.utils

import com.google.gson.Gson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
//https://cdn.jsdelivr.net/gh/msusman1/devineScript/branch/allBranches
object DevineUtils {
    val baseUrl = "https://www.miracles-of-quran.com/"
    fun parseBranches(url: String = baseUrl): List<BranchModel> {
        val branches = mutableListOf<BranchModel>()
        val document = Jsoup.connect(url).timeout(10000).userAgent("Mozilla").get()
        val body = document.body()
        val features = body.getElementsByClass("features2")
        val allAs: List<String> = features.flatMap { it.getElementsByTag("a") }.map { baseUrl + it.attr("href") }
        allAs.forEachIndexed { index, href ->
            val document1 = Jsoup.connect(href).timeout(10000).userAgent("Mozilla").get()
            val newBody = document1.body()
            val header = newBody.getElementsByClass("header1").firstOrNull()
            val titles = header?.getElementsByClass("mbr-section-title")?.text() ?: ""
            val subtitles = header?.getElementsByClass("mbr-section-subtitle")?.text() ?: ""
            var fullPath = ""
            header?.let { fullPath = extractBackdrop(document1, it) }
            branches.add(BranchModel(index + 1, titles, subtitles, fullPath, href))
        }
        return branches
    }

    fun parseDomain(): Int {
        val fileContent = File("branches/all.json").readText()
        val allBranches = Gson().fromJson(fileContent, BaseResponse::class.java).data
        var domainId = 113
        allBranches.forEach { branchModel ->
            val deomains = mutableListOf<DomainModel>()
            val document = Jsoup.connect(branchModel.href).timeout(10000).userAgent("Mozilla").get()
            val body = document.body()
            val features = body.getElementsByClass("features2")
            val allAs: List<String> = features.flatMap { it.getElementsByTag("a") }.map { baseUrl + it.attr("href") }
            allAs.forEachIndexed { index, href ->
                val document1 = Jsoup.connect(href).timeout(10000).userAgent("Mozilla").get()
                val newBody = document1.body()
                val allSectionElements = newBody.getElementsByTag("section")
                    .filter { it.id().startsWith("content") || it.id().startsWith("image") }.toMutableList()
                val header = allSectionElements.removeFirst()
                val subtitleelement = allSectionElements.removeFirst()
                val titles = header?.getElementsByClass("mbr-section-title")?.text() ?: ""
                val subtitles = subtitleelement?.text() ?: ""
                var fullPath = ""
                header?.let { fullPath = extractBackdrop(document1, it) }
                deomains.add(DomainModel(domainId++, titles, subtitles, fullPath, branchModel.branchId, href))
            }
            val domainresp=Gson().toJson(BaseResponseDomain(true, data = deomains))
            File("domains/${branchModel.branchId}.json").writeText(domainresp)
        }

        return domainId
    }


    suspend fun parseDemonstrations(url: String): Demonstration? {
        if (url.isBlank()) {
            return null
        }
        val sections = mutableListOf<Section>()
        val document = Jsoup.connect(url).timeout(10000).userAgent("Mozilla").get()

        val allSectionElements = document.body().getElementsByTag("section")
            .filter { it.id().startsWith("content") || it.id().startsWith("image") }.toMutableList()
        val backDropElement = allSectionElements.removeFirst() //bg image section removed
        val fullPath = extractBackdrop(document, backDropElement)
        val difficultyLevel = extractDifficultyLevel(backDropElement)
        allSectionElements.removeFirst()//title section removed
        allSectionElements.forEach {
            if (it.id().startsWith("image")) {   //is image
                sections.add(extractImageSection(it))
            } else if (it.hasAnyChildClass("mbr-col-lg-12")) { //is paragraph
                sections.add(extractParagraphSection(it))
            } else if (it.hasAnyChildWithTag("blockquote") && textContainsArabic(it.text())) { //is quranic reference
                sections.add(extractQuranicReferenceSection(it))
            } else if (it.hasAnyChildWithTag("blockquote")) { //is general reference
                sections.add(extractGeneralReferenceSection(it))
            } else if (it.hasAnyChildWithTag("h2")) { //is moral
                sections.add(extractMoralSection(it))
            }
        }
        return Demonstration(difficultyLevel, fullPath, sections)
    }

    private fun extractParagraphSection(element: Element): Section {
        return Section(SectionTypeEnum.PARAGRAPH, content = element.getElementsByTag("p").html())
    }

    private fun extractMoralSection(element: Element): Section {
        return Section(SectionTypeEnum.MORAL, content = element.text())
    }

    private fun extractGeneralReferenceSection(element: Element): Section {
        val blockQuote = element.getElementsByTag("blockquote").first()!!
        val allPs = blockQuote.children().filter { it.text().isNullOrEmpty().not() }
        var head = ""
        var referenceLink = ""
        var referenceTitle = ""
        val stringBuilder = StringBuilder()
        allPs.forEachIndexed { index, p ->
            if (p.hasAnyChildWithTag("a")) {
                val a = p.getElementsByTag("a").first()
                referenceTitle = a?.text() ?: ""
                referenceLink = a?.attr("href") ?: ""
            } else if (p.hasAnyChildWithTag("strong")) {
                head = p.text()
            } else {
                stringBuilder.append(p.text()).append("<br>")

            }

        }
        return Section(
            SectionTypeEnum.GENERAL_REFERENCE,
            content = stringBuilder.removeSuffix("<br>").toString(),
            heading = head,
            reference_title = referenceTitle,
            reference_link = referenceLink
        )
    }

    private fun extractQuranicReferenceSection(element: Element): Section {
        val blockQuote = element.getElementsByTag("blockquote")
        val allPs = blockQuote.flatMap { it.children() }.filter { it.text().isNullOrEmpty().not() }
        var head = ""
        var referenceLink = ""
        var referenceTitle = ""
        val stringBuilder = StringBuilder()
        val stringBuilderAr = StringBuilder()
        allPs.forEachIndexed { index, p ->
            if (p.hasAnyChildWithTag("a")) {
                val a = p.getElementsByTag("a").first()
                referenceTitle = a?.text() ?: ""
                referenceLink = a?.attr("href") ?: ""
                a?.siblingElements()?.forEach {
                    stringBuilder.append(p.text()).append("<br>")
                }
            } else if (p.hasAnyChildWithTag("strong")) {
                head = p.text()
            } else {
                if (textContainsArabic(p.text())) {
                    stringBuilderAr.append(p.text()).append("<br>")
                } else {
                    stringBuilder.append(p.text()).append("<br>")
                }

            }

        }
        return Section(
            SectionTypeEnum.QURAN_REFERENCE,
            content = stringBuilder.removeSuffix("<br>").toString(),
            heading = head,
            reference_title = referenceTitle,
            reference_link = referenceLink,
            content_ar = stringBuilderAr.removeSuffix("<br>").toString()
        )
    }

    private fun extractImageSection(element: Element): Section {
        val link = element.getElementsByTag("amp-img").firstOrNull()?.attr("src") ?: ""
        return Section(SectionTypeEnum.IMAGE, content = "https://www.miracles-of-quran.com/$link")
    }

    private fun extractDifficultyLevel(backDropElement: Element): DifficultyLevel {

        val str = backDropElement.getElementsByClass("mbr-section-subtitle").firstOrNull()?.html() ?: ""
        val level = str.split("-").getOrNull(1)?.trim()?.toUpperCase()
        return kotlin.runCatching { DifficultyLevel.valueOf(level ?: "") }.getOrNull() ?: DifficultyLevel.UNKNOWN
    }

    private fun extractBackdrop(document: Document, backDropElement: Element): String {
        val backdropClassForbg = backDropElement.classNames().find { it.startsWith("cid") }
        val bgStyle = document.getElementsByTag("style").find { it.hasAttr("amp-custom") }
        val str = bgStyle?.html()?.replace(" ", "") ?: ""
        val startIndex = str.indexOf("$backdropClassForbg{")
        var endIndex = str.length
        for (i in startIndex..str.length) {
            if (str[i] == '}') {
                endIndex = i;break; }
        }
        val newStr = str.substring(startIndex, endIndex)
        val fullPath =
            baseUrl + newStr.substring(newStr.indexOf("assets/"), newStr.indexOf("\");"))
        return fullPath
    }
}



fun textContainsArabic(text: String): Boolean {
    for (charac in text.toCharArray()) {
        if (Character.UnicodeBlock.of(charac) === Character.UnicodeBlock.ARABIC) {
            return true
        }
    }
    return false
}

data class Demonstration(val difficultyLevel: DifficultyLevel, val backdrop: String, val sections: List<Section>)
data class Section(
    val section_type: SectionTypeEnum,
    val content: String,
    val content_ar: String? = null,
    val heading: String? = null,
    val reference_title: String? = null,
    val reference_link: String? = null,
)

enum class DifficultyLevel {
    EASY,
    INTERMEDIATE,
    ADVANCED,
    EXTREME,
    UNKNOWN,
}

enum class SectionTypeEnum {
    GENERAL_REFERENCE,
    PARAGRAPH,
    QURAN_REFERENCE,
    MORAL,
    IMAGE,
}

data class BranchModel(
    val branchId: Int,
    val title: String,
    val subtitle: String,
    val thumbnail: String,
    val href: String
)

data class DomainModel(
    val domainId: Int,
    val title: String,
    val subtitle: String,
    val thumbnail: String,
    val branchId: Int,
    val href: String
)

data class BaseResponse(val success: Boolean, val data: List<BranchModel>)
data class BaseResponseDomain(val success: Boolean, val data: List<DomainModel>)