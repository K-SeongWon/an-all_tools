# 안올툴 릴리즈 서명 키를 만들고 GitHub Actions Secrets 에 등록한다. (처음 한 번만 실행)
#
#   pwsh scripts/setup-signing.ps1
#
# - 키 파일과 비밀번호는 ~/.anall/ 에만 저장되고 저장소에는 올라가지 않는다.
# - 이미 키가 있으면 새로 만들지 않고 Secrets 만 다시 등록한다.
# - ~/.anall 폴더는 반드시 따로 백업한다. 키를 잃어버리면 앱을 업데이트할 수 없고 지우고 다시 설치해야 한다.

$ErrorActionPreference = 'Stop'
$repo  = 'K-SeongWon/an-all_tools'
$dir   = Join-Path $HOME '.anall'
$jks   = Join-Path $dir 'anall-release.jks'
$props = Join-Path $dir 'keystore.properties'
$alias = 'anall'

New-Item -ItemType Directory -Force $dir | Out-Null

if (-not (Test-Path $jks)) {
    $keytool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/keytool.exe'))) {
        Join-Path $env:JAVA_HOME 'bin/keytool.exe'
    } else {
        (Get-Command keytool -ErrorAction Stop).Source
    }

    # 무작위 비밀번호 (영문·숫자 32자). 화면에 출력하지 않는다.
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $pw = ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]', '').Substring(0, 32)

    # 인증서는 공개 APK 에 들어가므로 이름 등 개인정보를 넣지 않는다.
    & $keytool -genkeypair -keystore $jks -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 `
        -validity 36500 -storepass $pw -keypass $pw -dname 'CN=An-All Tools' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool 실패' }

    $storePath = $jks -replace '\', '/'
    [IO.File]::WriteAllText($props, "storeFile=$storePath`nstorePassword=$pw`nkeyAlias=$alias`nkeyPassword=$pw`n")
    Write-Host "새 서명 키를 만들었습니다: $jks"
} else {
    Write-Host "기존 서명 키를 사용합니다: $jks"
}

$map = @{}
Get-Content $props | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $map[$Matches[1]] = $Matches[2] }
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
gh secret set ANALL_KEYSTORE_BASE64   --repo $repo --body $b64
gh secret set ANALL_KEYSTORE_PASSWORD --repo $repo --body $map['storePassword']
gh secret set ANALL_KEY_ALIAS         --repo $repo --body $map['keyAlias']
gh secret set ANALL_KEY_PASSWORD      --repo $repo --body $map['keyPassword']
if ($LASTEXITCODE -ne 0) { throw 'gh secret set 실패' }

Write-Host ''
Write-Host 'GitHub Secrets 등록 완료. 이제 릴리즈 빌드가 이 키로 서명됩니다.'
Write-Host "꼭 백업하세요: $dir  (anall-release.jks, keystore.properties)"
