# Instruções de Configuração

## Problema: gradle-wrapper.jar ausente

Se você encontrar o erro:
```
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

Isso significa que o arquivo `gradle/wrapper/gradle-wrapper.jar` está faltando.

## Solução 1: Download Manual (Recomendado)

1. Acesse o link: https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar
2. Baixe o arquivo
3. Coloque o arquivo em: `gradle/wrapper/gradle-wrapper.jar`

## Solução 2: Usar o Script PowerShell

Execute o script fornecido:
```powershell
.\download-wrapper.ps1
```

**Nota:** Se o script falhar devido a rate limiting do GitHub, use a Solução 1.

## Solução 3: Usar GitHub Actions (Recomendado)

O GitHub Actions baixa automaticamente o arquivo durante o build. Você não precisa fazer nada:

1. Faça push do código
2. O workflow será executado automaticamente
3. Baixe o APK gerado na aba Actions

## Verificação

Após baixar o arquivo, verifique se ele existe:
```powershell
Test-Path gradle\wrapper\gradle-wrapper.jar
```

Deve retornar `True`.


