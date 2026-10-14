Add-Type -AssemblyName System.IO.Compression.FileSystem

$clipcutAssets = "C:\Users\xbxga\.gemini\antigravity\scratch\clipcut\app\src\main\assets"
$dumpApks = "G:\Marvis\conv_3e41efc10e1c4cbc86fa72ce834bcb39\temp\apks"
$tempDir = "G:\Marvis\conv_3e41efc10e1c4cbc86fa72ce834bcb39\temp"

# 1. EXTRACT SOUND EFFECTS & MUSIC FROM FILMORA
Write-Host ">>> [1/5] Extracting SFX & BGM from Filmora..."
$sfxDir = Join-Path $clipcutAssets "audio\sfx"
$bgmDir = Join-Path $clipcutAssets "audio\bgm"
New-Item -ItemType Directory -Force -Path $sfxDir | Out-Null
New-Item -ItemType Directory -Force -Path $bgmDir | Out-Null

$audioSoundZipPath = Join-Path $tempDir "audioSoundPreset.zip"
if (Test-Path $audioSoundZipPath) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($audioSoundZipPath)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -match '\.(mp3|wav)$') {
            $destName = $entry.Name -replace " - AISFX", "" -replace " ", "_"
            $destPath = Join-Path $sfxDir $destName
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
            Write-Host "  Extracted SFX: $destName ($([math]::Round($entry.Length/1KB, 1)) KB)"
        }
    }
    $zip.Dispose()
}

$newAudioZipPath = Join-Path $tempDir "new_audio_preset.zip"
if (Test-Path $newAudioZipPath) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($newAudioZipPath)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -match '\.mp3$') {
            if ($entry.Name -like "*Autumn*") {
                $destPath = Join-Path $bgmDir "autumn_afternoon_lofi.mp3"
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
                Write-Host "  Extracted BGM: autumn_afternoon_lofi.mp3 ($([math]::Round($entry.Length/1MB, 2)) MB)"
            } else {
                $destName = $entry.Name -replace " - AISFX", "" -replace " ", "_"
                $destPath = Join-Path $sfxDir $destName
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
                Write-Host "  Extracted Meme SFX: $destName"
            }
        }
    }
    $zip.Dispose()
}

# 2. EXTRACT BGM & SFX FROM VN
Write-Host ">>> [2/5] Extracting BGM & SFX from VN..."
$vnApk = Join-Path $dumpApks "extracted\VN_base.apk"
if (Test-Path $vnApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($vnApk)
    $santa = ($zip.Entries | Where-Object { $_.FullName -like "*res/raw/santa.mp3*" })[0]
    if ($santa) {
        $destPath = Join-Path $bgmDir "holiday_acoustic_santa.mp3"
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($santa, $destPath, $true)
        Write-Host "  Extracted BGM: holiday_acoustic_santa.mp3 ($([math]::Round($santa.Length/1MB, 2)) MB)"
    }
    $scanBeep = ($zip.Entries | Where-Object { $_.FullName -like "*res/raw/camera_scan_beep.mp3*" })[0]
    if ($scanBeep) {
        $destPath = Join-Path $sfxDir "camera_scan_beep.mp3"
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($scanBeep, $destPath, $true)
        Write-Host "  Extracted SFX: camera_scan_beep.mp3"
    }

    # Overlay / Frame masks
    $overlayDir = Join-Path $clipcutAssets "overlays\frames"
    New-Item -ItemType Directory -Force -Path $overlayDir | Out-Null
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/Overlay/cover/*.png") {
            $destPath = Join-Path $overlayDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
            Write-Host "  Extracted Frame Overlay: $($entry.Name)"
        }
    }
    $zip.Dispose()
}

# 3. EXTRACT CREATOR FONTS FROM FILMORA
Write-Host ">>> [3/5] Extracting Creator Typography Fonts from Filmora..."
$fontsDir = Join-Path $clipcutAssets "fonts"
$textFontsZip = Join-Path $tempDir "text_fonts.zip"
if (Test-Path $textFontsZip) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($textFontsZip)
    $curatedFonts = @(
        "Montserrat-Regular.ttf",
        "Montserrat-ExtraBold_0.ttf",
        "Cinzel Decorative Bold.ttf",
        "Gochi Hand.ttf",
        "Bangers Regular.ttf",
        "AlfaSlabOne-Regular.ttf",
        "Caveat-VariableFont_wght.ttf",
        "HeavyweightImpact-Bold.otf",
        "Barlow-Bold.ttf",
        "Typewriter.otf",
        "HoneyJar.otf",
        "Playball-Regular.ttf"
    )
    foreach ($entry in $zip.Entries) {
        foreach ($target in $curatedFonts) {
            if ($entry.Name -like "*$target*") {
                $cleanName = $entry.Name -replace "^\d+_", "" -replace "_0\.ttf$", ".ttf" -replace " ", "_"
                $destPath = Join-Path $fontsDir $cleanName
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
                Write-Host "  Extracted Font: $cleanName"
            }
        }
    }
    $zip.Dispose()
}

# 4. EXTRACT POWERDIRECTOR LUT THUMBNAILS
Write-Host ">>> [4/5] Extracting PowerDirector LUT Thumbnails..."
$pdApk = Join-Path $dumpApks "extracted\PowerDirector_base.apk"
$lutThumbDir = Join-Path $clipcutAssets "luts\thumbs"
New-Item -ItemType Directory -Force -Path $lutThumbDir | Out-Null
if (Test-Path $pdApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($pdApk)
    # Find all LUT folders that have .cube and Thumb.png
    $cubeEntries = $zip.Entries | Where-Object { $_.FullName -like "assets/LUT/*/*.cube" }
    foreach ($cube in $cubeEntries) {
        $folder = $cube.FullName.Substring(0, $cube.FullName.LastIndexOf('/'))
        $thumbName = "$folder/Thumb.png"
        $thumbEntry = ($zip.Entries | Where-Object { $_.FullName -eq $thumbName })[0]
        if ($thumbEntry) {
            $baseName = [System.IO.Path]::GetFileNameWithoutExtension($cube.Name) -replace " ", "_"
            $destPath = Join-Path $lutThumbDir "${baseName}_thumb.png"
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($thumbEntry, $destPath, $true)
            Write-Host "  Extracted LUT Thumb: ${baseName}_thumb.png"
        }
    }
    $zip.Dispose()
}

# 5. EXTRACT ANIMATED STICKER FRAMES FROM INSHOT
Write-Host ">>> [5/5] Extracting Animated Emoji Sticker Frames from InShot..."
$inshotApk = Join-Path $dumpApks "base_inshot.apk"
$aniemojiDir = Join-Path $clipcutAssets "stickers\aniemoji_frames"
New-Item -ItemType Directory -Force -Path $aniemojiDir | Out-Null
if (Test-Path $inshotApk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($inshotApk)
    # Extract frames for aniemoji packs 01..04
    $frames = $zip.Entries | Where-Object { $_.FullName -match 'assets/aniemoji01/aniemoji01_0[1-4]/\d+\.png$' }
    foreach ($entry in $frames) {
        $parts = $entry.FullName.Split('/')
        $pack = $parts[2]
        $file = $parts[3]
        $destPath = Join-Path $aniemojiDir "${pack}_frame_${file}"
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
    }
    Write-Host "  Extracted $($frames.Count) animated sticker frames into stickers/aniemoji_frames/"
    $zip.Dispose()
}

Write-Host "`nAll assets extracted successfully into $clipcutAssets!"
