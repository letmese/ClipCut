# Bundle ClipCut source code
$ErrorActionPreference = "Stop"

$workspace = "C:\Users\xbxga\.gemini\antigravity\scratch\clipcut"
$desktopOutput = "C:\Users\xbxga\OneDrive\Desktop\clip_cut_all_code.txt"
$downloadsOutput = "C:\Users\xbxga\Downloads\clip_cut_all_code.txt"

$timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$header = @"
================================================================================
CLIP CUT - ALL SOURCE CODE BUNDLE
Version: 1.2.0 (Build 17)
Generated: $timestamp
================================================================================

"@

$files = @(
    ".\app\build.gradle",
    ".\app\src\main\AndroidManifest.xml"
)

$ktFiles = Get-ChildItem -Path "$workspace\app\src\main\java" -Filter "*.kt" -Recurse | 
    Sort-Object FullName | 
    ForEach-Object { ".\" + (Resolve-Path -Path $_.FullName -Relative) }

$resFiles = @(
    ".\app\src\main\res\drawable\ic_launcher_bg.xml",
    ".\app\src\main\res\drawable\ic_launcher_fg.xml",
    ".\app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml",
    ".\app\src\main\res\values\strings.xml",
    ".\app\src\main\res\values\styles.xml",
    ".\app\src\main\res\xml\file_paths.xml",
    ".\build.gradle",
    ".\gradle.properties",
    ".\gradle\wrapper\gradle-wrapper.properties"
)

$allFiles = $files + $ktFiles + $resFiles

$sb = [System.Text.StringBuilder]::new()
[void]$sb.Append($header)

Push-Location $workspace
try {
    foreach ($f in $allFiles) {
        if (Test-Path $f) {
            [void]$sb.AppendLine("================================================================================")
            [void]$sb.AppendLine("FILE: $f")
            [void]$sb.AppendLine("================================================================================")
            $content = Get-Content -Path $f -Raw
            [void]$sb.AppendLine($content)
            [void]$sb.AppendLine()
        }
    }
} finally {
    Pop-Location
}

$outputContent = $sb.ToString()
[System.IO.File]::WriteAllText($desktopOutput, $outputContent, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($downloadsOutput, $outputContent, [System.Text.Encoding]::UTF8)

Write-Host "Bundled $($allFiles.Count) files into:"
Write-Host "  $desktopOutput ($($outputContent.Length) chars)"
Write-Host "  $downloadsOutput ($($outputContent.Length) chars)"
