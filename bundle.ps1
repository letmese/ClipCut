$rootDir = "C:\Users\xbxga\.gemini\antigravity\scratch\clipcut"
$outPath = "C:\Users\xbxga\Downloads\clip_cut_all_code.txt"

$files = @(
    "build.gradle.kts",
    "settings.gradle.kts",
    "app\build.gradle.kts"
) | ForEach-Object { Join-Path $rootDir $_ } | Where-Object { Test-Path $_ }

$srcFiles = Get-ChildItem -Path (Join-Path $rootDir "app\src\main") -Recurse -File | Where-Object {
    $_.Extension -match "\.(kt|java|xml|glsl|vert|frag)$"
}

$allFiles = @($files) + @($srcFiles | ForEach-Object { $_.FullName })
Write-Host "Total files to bundle: $($allFiles.Count)"

$sb = New-Object System.Text.StringBuilder
foreach ($f in $allFiles) {
    $rel = $f.Substring($rootDir.Length).TrimStart("\", "/")
    [void]$sb.AppendLine("================================================================================")
    [void]$sb.AppendLine("FILE: $rel")
    [void]$sb.AppendLine("================================================================================")
    $content = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
    [void]$sb.AppendLine($content)
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("")
}

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), [System.Text.Encoding]::UTF8)
$fi = Get-Item $outPath
Write-Host "Bundle created: $($fi.FullName) ($($fi.Length) bytes, $([math]::Round($fi.Length / 1KB, 1)) KB)"
