# MRIT Server

Aplicativo Android para Gateway Tuya.

## 📱 Sobre o Projeto

Este projeto é um aplicativo Android desenvolvido para funcionar como servidor/gateway para dispositivos Tuya.

### ✨ Funcionalidades

- ✅ Interface moderna com Material Design 3
- ✅ Lista de dispositivos Tuya conectados
- ✅ Controle de dispositivos (ligar/desligar)
- ✅ Status em tempo real (online/offline)
- ✅ Atualização manual de dispositivos
- ✅ Visualização de informações dos dispositivos
- ✅ APK assinado automaticamente no GitHub Actions

### 📝 Nota sobre Integração

O aplicativo atualmente utiliza dados de exemplo (mock) para demonstração. Para integração completa com a API Tuya, será necessário:

1. Adicionar SDK da Tuya ou implementar chamadas HTTP para a API
2. Configurar credenciais de autenticação
3. Substituir as funções mock em `MainActivity.kt` pelas chamadas reais

## 🚀 Como Gerar o APK

### ⚠️ Importante: Arquivo gradle-wrapper.jar

Se você estiver fazendo build local, certifique-se de que o arquivo `gradle/wrapper/gradle-wrapper.jar` existe. Se não existir:

1. **Baixe manualmente:**
   - Acesse: https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar
   - Salve o arquivo em: `gradle/wrapper/gradle-wrapper.jar`

2. **Ou execute o script:**
   ```powershell
   .\download-wrapper.ps1
   ```

**Nota:** O GitHub Actions baixa automaticamente este arquivo durante o build, então não é necessário para builds no GitHub.

### Opção 1: GitHub Actions (Recomendado - Sem Android Studio)

O projeto está configurado com GitHub Actions para gerar o APK automaticamente. Siga estes passos:

1. **O código já foi enviado para o repositório!** ✅

2. **Acesse o GitHub:**
   - Vá para: https://github.com/MRITSoftware/mritserver
   - Clique na aba **Actions**
   - Aguarde o workflow "Build APK" completar
   - Clique no workflow concluído
   - Na seção **Artifacts**, baixe o arquivo `app-release`

3. **O APK estará disponível para download por 30 dias**
4. **O APK será assinado automaticamente** com uma keystore gerada durante o build

### Opção 2: Build Local (Requer Android SDK)

Se você tiver o Android SDK instalado localmente:

```bash
# Windows
gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

O APK será gerado em: `app/build/outputs/apk/release/app-release.apk`

## 📋 Requisitos

- **MinSdk:** 24 (Android 7.0)
- **TargetSdk:** 34 (Android 14)
- **CompileSdk:** 34

## 🛠️ Tecnologias Utilizadas

- Kotlin
- AndroidX
- Material Design Components
- Gradle 8.2

## 📂 Estrutura do Projeto

```
mritserver/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/mritsoftware/mritserver/
│   │       │   └── MainActivity.kt
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   ├── values/
│   │       │   └── ...
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── .github/
│   └── workflows/
│       └── build-apk.yml
├── build.gradle
├── settings.gradle
└── README.md
```

## 🔧 Configuração

### Variáveis de Ambiente (se necessário)

Se o projeto precisar de chaves de API ou configurações específicas, você pode adicioná-las como secrets no GitHub:

1. Vá em **Settings** > **Secrets and variables** > **Actions**
2. Adicione as variáveis necessárias
3. Use-as no workflow através de `${{ secrets.NOME_DA_VARIAVEL }}`

## 📝 Desenvolvimento

### Adicionar Dependências

Edite o arquivo `app/build.gradle` e adicione as dependências necessárias na seção `dependencies`.

### Modificar o App

- **Activity Principal:** `app/src/main/java/com/mritsoftware/mritserver/MainActivity.kt`
- **Layout:** `app/src/main/res/layout/activity_main.xml`
- **Recursos:** `app/src/main/res/values/`

## 📄 Licença

Este projeto é propriedade da MRIT Software.

## 🤝 Contribuindo

1. Faça um fork do projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📞 Suporte

Para suporte, entre em contato através do repositório GitHub.
