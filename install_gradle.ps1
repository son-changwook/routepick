$gradleVersion = "8.5"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$gradleZip = "gradle-$gradleVersion-bin.zip"
$gradleDir = "C:\Gradle\gradle-$gradleVersion"

# Gradle 다운로드
Invoke-WebRequest -Uri $gradleUrl -OutFile $gradleZip

# 압축 해제
Expand-Archive -Path $gradleZip -DestinationPath "C:\Gradle"

# 환경 변수 설정
$envPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($envPath -notlike "*$gradleDir\bin*") {
    [Environment]::SetEnvironmentVariable("Path", "$envPath;$gradleDir\bin", "User")
}

# 임시 파일 삭제
Remove-Item $gradleZip

Write-Host "Gradle $gradleVersion이 설치되었습니다. 새 터미널을 열어주세요." 