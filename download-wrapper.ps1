# Script para baixar o gradle-wrapper.jar
$wrapperPath = "gradle\wrapper\gradle-wrapper.jar"

if (Test-Path $wrapperPath) {
    Write-Host "gradle-wrapper.jar já existe!"
    exit 0
}

Write-Host "Baixando gradle-wrapper.jar..."

# Tentar múltiplas fontes
$sources = @(
    "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar",
    "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
)

$success = $false
foreach ($url in $sources) {
    try {
        Write-Host "Tentando baixar de: $url"
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $url -OutFile $wrapperPath -UseBasicParsing -TimeoutSec 30
        if (Test-Path $wrapperPath) {
            $fileSize = (Get-Item $wrapperPath).Length
            if ($fileSize -gt 0) {
                Write-Host "Download concluído! Tamanho: $fileSize bytes"
                $success = $true
                break
            }
        }
    } catch {
        Write-Host "Erro ao baixar de $url : $_"
    }
}

if (-not $success) {
    Write-Host "Não foi possível baixar o arquivo automaticamente."
    Write-Host "Por favor, baixe manualmente de:"
    Write-Host "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
    Write-Host "E coloque em: gradle\wrapper\gradle-wrapper.jar"
    exit 1
}

exit 0


