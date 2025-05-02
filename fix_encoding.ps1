$files = Get-ChildItem -Path "routepick-common/src/main/java/com/routepick" -Recurse -Filter "*.java"
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $content = $content -replace "^\xEF\xBB\xBF", ""
    [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
} 