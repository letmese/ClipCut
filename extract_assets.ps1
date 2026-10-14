Add-Type -AssemblyName System.IO.Compression.FileSystem
$targetRoot = "C:\Users\xbxga\.gemini\antigravity\scratch\clipcut\app\src\main\assets"

# 1. InShot Assets
$inshotApk = "G:\Marvis\conv_3e41efc10e1c4cbc86fa72ce834bcb39\temp\apks\base_inshot.apk"
if (Test-Path $inshotApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($inshotApk)

    # Audio transitions
    $transDir = Join-Path $targetRoot "audio\transitions"
    New-Item -ItemType Directory -Force -Path $transDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/transition_audio/*.mp3") {
            $dest = Join-Path $transDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted transition audio: $($entry.Name)"
        }
    }

    # Voice change sample audio
    $voiceDir = Join-Path $targetRoot "audio\voice"
    New-Item -ItemType Directory -Force -Path $voiceDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/voice_change_audio/*.mp3") {
            $dest = Join-Path $voiceDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted voice audio: $($entry.Name)"
        }
    }

    # Fonts
    $fontDir = Join-Path $targetRoot "fonts"
    New-Item -ItemType Directory -Force -Path $fontDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match '^assets/[^/]+\.(ttf|otf)$') {
            $dest = Join-Path $fontDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted font: $($entry.Name)"
        }
    }

    # Stickers / animated emojis covers
    $stickerDir = Join-Path $targetRoot "stickers\aniemoji"
    New-Item -ItemType Directory -Force -Path $stickerDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/aniemoji01/*/cover.png") {
            $parts = $entry.FullName.Split('/')
            $folderName = $parts[2]
            $dest = Join-Path $stickerDir "${folderName}_cover.png"
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted sticker: $dest"
        }
    }
    $zip.Dispose()
}

# 2. VN Assets
$vnApk = "G:\Marvis\conv_3e41efc10e1c4cbc86fa72ce834bcb39\temp\apks\extracted\VN_base.apk"
if (Test-Path $vnApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($vnApk)

    # LUT filters
    $lutDir = Join-Path $targetRoot "luts"
    New-Item -ItemType Directory -Force -Path $lutDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/filters/IS/*.png" -or $entry.FullName -like "assets/filters/Food/*.png") {
            $dest = Join-Path $lutDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted VN LUT: $($entry.Name)"
        }
    }

    # Voice icons
    $iconDir = Join-Path $targetRoot "icons\voice"
    New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/audio/icon_voice_*.png") {
            $dest = Join-Path $iconDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted VN voice icon: $($entry.Name)"
        }
    }
    $zip.Dispose()
}

# 3. PowerDirector LUTs
$pdApk = "G:\Marvis\conv_3e41efc10e1c4cbc86fa72ce834bcb39\temp\apks\extracted\PowerDirector_base.apk"
if (Test-Path $pdApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($pdApk)
    $cubeDir = Join-Path $targetRoot "luts\cube"
    New-Item -ItemType Directory -Force -Path $cubeDir | Out-Null
    $count = 0
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/LUT/*/*.cube" -and $count -lt 30) {
            $dest = Join-Path $cubeDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "Extracted PD .cube LUT: $($entry.Name)"
            $count++
        }
    }
    $zip.Dispose()
}
Write-Host "Asset extraction complete!"
