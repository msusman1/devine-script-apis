

function onOkClick(relatedPath, newPageLink) {
    console.log("function clicked");
    window.open(newPageLink, '_blank')
    window.location.href = relatedPath
}