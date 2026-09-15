param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [string]$NdkVersion = '30.0.16138531'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    git submodule update --init --recursive
    if ($LASTEXITCODE -ne 0) { throw 'Submodule download failed' }
    $nativeRoot = (Resolve-Path 'hev-socks5-tunnel').Path
    # Git for Windows may check symbolic header links out as short text files.
    foreach ($repo in @('.', 'src/core', 'third-part/hev-task-system', 'third-part/lwip', 'third-part/yaml')) {
        $repoDir = Join-Path $nativeRoot $repo
        foreach ($entry in (git -C $repoDir ls-files -s)) {
            if ($entry -match '^120000 ([a-f0-9]+) 0\t(.+)$') {
                $blobId = $Matches[1]
                $linkPath = Join-Path $repoDir $Matches[2]
                $relativeTarget = (git -C $repoDir cat-file blob $blobId).Trim()
                $targetPath = [IO.Path]::GetFullPath((Join-Path (Split-Path $linkPath -Parent) $relativeTarget))
                if (!$targetPath.StartsWith($nativeRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Header target escapes native source tree' }
                $item = Get-Item -LiteralPath $linkPath
                if (!($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                    Copy-Item -LiteralPath $targetPath -Destination $linkPath -Force
                }
            }
        }
    }
    New-Item -ItemType Directory -Path V2rayNG/app/libs -Force | Out-Null
    $aar = 'V2rayNG/app/libs/libv2ray.aar'
    if (!(Test-Path $aar)) { Invoke-WebRequest 'https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.9.9/libv2ray.aar' -OutFile $aar }
    $expected = (Get-Content (Join-Path $PSScriptRoot 'libv2ray.sha256')).Trim()
    if ((Get-FileHash $aar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Xray AAR checksum mismatch' }
    $ndkBuild = Join-Path $SdkPath "ndk/$NdkVersion/ndk-build.cmd"
    & $ndkBuild NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=hev-socks5-tunnel/Android.mk 'APP_ABI=arm64-v8a armeabi-v7a x86_64 x86' APP_PLATFORM=android-24 NDK_LIBS_OUT=V2rayNG/app/libs NDK_OUT=native-build/obj 'APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service' 'APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu' -j4
    if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
    foreach ($abi in @('arm64-v8a','armeabi-v7a','x86_64','x86')) {
        Copy-Item "V2rayNG/app/libs/$abi/hev-socks5-tunnel-bin" "V2rayNG/app/libs/$abi/libhevsockstun.so" -Force
    }
    Set-Content V2rayNG/local.properties ('sdk.dir=' + $SdkPath.Replace('\','/'))
    & .\V2rayNG\gradlew.bat -p V2rayNG :app:testPlaystoreDebugUnitTest :app:assemblePlaystoreDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed' }
} finally { Pop-Location }
