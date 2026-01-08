# 📱 Funcionalidades da Nova Versão - MRIT Server

## 🎯 Visão Geral

Esta versão inclui melhorias significativas na recuperação automática após quedas de internet, integração completa com Supabase para sincronização de dispositivos, e uma interface melhorada para gerenciamento de dispositivos Tuya.

---

## ✨ Principais Funcionalidades

### 1. 🔄 Recuperação Automática após Queda de Internet

#### Problema Resolvido
Quando a internet cai por mais de 30 minutos, o servidor para de funcionar e não se recupera automaticamente.

#### Solução Implementada

**a) NetworkChangeReceiver**
- Detecta automaticamente quando a internet volta
- Monitora mudanças de conectividade de rede
- Verifica se o servidor está respondendo após reconexão
- Reinicia o servidor automaticamente se necessário

**b) Health Check Periódico**
- Verifica a cada 1 minuto se o servidor está respondendo
- Executa no `PythonServerService` em background
- Reinicia automaticamente se o servidor não responder
- Evita que o servidor fique travado sem detecção

**c) Melhorias no Servidor Python**
- Timeout de 30 segundos em operações de rede (`deviceScan()`)
- Evita travamentos em operações bloqueadas
- Limpeza automática de cache quando há erros de conexão
- Tratamento robusto de erros em todas as operações

**Como Funciona:**
1. Quando a internet cai: O servidor continua tentando operar, mas com timeouts para evitar travamentos
2. Quando a internet volta:
   - O `NetworkChangeReceiver` detecta a reconexão
   - Aguarda 5 segundos para a rede estabilizar
   - Verifica se o servidor está respondendo
   - Se não estiver, reinicia automaticamente
3. Monitoramento contínuo: O health check verifica a cada minuto e reinicia se necessário

---

### 2. 🗄️ Integração com Supabase

#### Funcionalidade
Sincronização automática de dispositivos Tuya encontrados na rede com a tabela `tuya_devices` no banco de dados Supabase.

#### Configuração Automática
- **URL:** `https://kihyhoqbrkwbfudttevo.supabase.co`
- **Anon Key:** Configurada automaticamente na inicialização
- Credenciais salvas em `config.json`
- Pode ser atualizada via API endpoint `/config/supabase`

#### Endpoints da API

**`POST /tuya/sync`**
Sincroniza devices encontrados na rede LAN com a tabela `tuya_devices`.

**Processo:**
1. Faz scan LAN para encontrar devices na rede
2. Busca na tabela `tuya_devices` os devices com mesmo `tuya_device_id`
3. Para cada match, atualiza automaticamente:
   - `lan_ip` (do scan)
   - `protocol_version` (do scan)
   - `name` (se fornecido no body)
   - `local_key` (se fornecido no body)
4. `updated_at` é atualizado automaticamente

**Body opcional:**
```json
{
  "devices": {
    "tuya_device_id": {
      "name": "Nome do Device",
      "local_key": "local_key_da_placa"
    }
  }
}
```

**`POST /config/supabase`**
Configura as credenciais do Supabase.

**Body:**
```json
{
  "url": "https://kihyhoqbrkwbfudttevo.supabase.co",
  "anon_key": "sua_anon_key"
}
```

#### Implementação Técnica
- Usa `requests` (biblioteca Python pura) para chamadas HTTP
- Evita dependências problemáticas como `pydantic-core` que requer Rust
- Compatível com Chaquopy no Android
- Funções implementadas:
  - `get_devices_from_db()` - Busca devices por `tuya_device_id`
  - `update_device_in_db()` - Atualiza campos na tabela

---

### 3. 📝 Sincronização ao Salvar Nome do Dispositivo

#### Funcionalidade
Quando o usuário edita e salva o nome de um dispositivo, o sistema automaticamente:
1. Busca o dispositivo na rede
2. Verifica se o código corresponde ao `tuya_device_id` na tabela
3. Sincroniza as informações com o Supabase

#### Fluxo Completo

**1. Interface de Edição**
- Campo de texto editável para o nome do dispositivo
- Botão "Salvar Nome e Sincronizar"

**2. Tela de Loading**
- Nova `LoadingSyncActivity` com:
  - ProgressBar animado
  - Texto "Conectando ao servidor..."
  - Subtítulo com status da operação em tempo real

**3. Processo de Sincronização**
```
Usuário salva nome
    ↓
Tela de loading aparece
    ↓
Busca dispositivos na rede (GET /tuya/devices)
    ↓
Verifica se tuya_device_id está na rede
    ↓
Se encontrado:
    ↓
Chama sincronização (POST /tuya/sync)
    ↓
Servidor atualiza no Supabase:
    - name
    - lan_ip
    - protocol_version
    ↓
Retorna para tela de detalhes
    ↓
Mostra mensagem de sucesso
```

**4. Atualização Automática**
- Após sincronização bem-sucedida:
  - Nome atualizado na UI
  - IP local atualizado (se encontrado)
  - Protocol version atualizado (se encontrado)

---

### 4. 🔢 Exibição de Protocol Version

#### Mudança Visual
No lugar de mostrar "Tipo: OTHER" na lista de dispositivos, agora mostra o `protocol_version` do dispositivo.

#### Implementação
- Adicionado campo `protocolVersion: String?` ao modelo `TuyaDevice`
- `DeviceAdapter` verifica:
  - Se `type == OTHER`: mostra `protocolVersion` (ex: "3.3", "3.4")
  - Se não for `OTHER`: mostra o tipo normal (ex: "LIGHT", "SWITCH")
- `MainActivity` captura `protocol_version` do scan LAN

#### Exemplo Visual
**Antes:**
```
Nome: Dispositivo abc123
Tipo: OTHER
Status: Online
```

**Depois:**
```
Nome: Lâmpada Sala
Tipo: 3.3
Status: Online
```

---

## 🔧 Melhorias Técnicas

### Dependências Adicionadas
- `androidx.activity:activity-ktx:1.8.2` - Para ActivityResultContracts
- `requests` (Python) - Para chamadas HTTP ao Supabase

### Arquivos Criados
- `NetworkChangeReceiver.kt` - Detecção de mudanças de rede
- `LoadingSyncActivity.kt` - Tela de sincronização
- `activity_loading_sync.xml` - Layout da tela de loading

### Arquivos Modificados
- `tuya_server.py` - Integração Supabase e sincronização
- `PythonServerService.kt` - Health check periódico
- `DeviceDetailsActivity.kt` - Edição de nome e sincronização
- `DeviceAdapter.kt` - Exibição de protocol_version
- `TuyaDevice.kt` - Adicionado campo protocolVersion
- `AndroidManifest.xml` - Registro de receivers e activities

---

## 📊 Fluxo de Dados

### Sincronização de Dispositivo

```
┌─────────────────┐
│  Usuário edita │
│     nome       │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│  Salva nome e   │
│  Sincronizar    │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Loading Screen  │
│ "Conectando..." │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Scan LAN        │
│ GET /tuya/devices│
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Verifica se     │
│ device_id está  │
│ na rede         │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Sincroniza      │
│ POST /tuya/sync │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Atualiza        │
│ Supabase:       │
│ - name          │
│ - lan_ip        │
│ - protocol_ver  │
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ Retorna sucesso │
│ Atualiza UI     │
└─────────────────┘
```

---

## 🎨 Interface do Usuário

### Tela de Detalhes do Dispositivo
- **Campo de nome editável** - Permite alterar o nome do dispositivo
- **Botão "Salvar Nome e Sincronizar"** - Inicia processo de sincronização
- **Informações exibidas:**
  - Nome do dispositivo (editável)
  - ID do dispositivo (mascarado)
  - IP Local
  - Status (Online/Offline)

### Tela de Loading
- **ProgressBar animado** - Indica processamento
- **Texto principal:** "Conectando ao servidor..."
- **Subtítulo dinâmico:**
  - "Buscando dispositivo na rede..."
  - "Dispositivo encontrado! Sincronizando com servidor..."
  - "Sincronização concluída com sucesso!"

### Lista de Dispositivos
- **Nome do dispositivo** - Ou ID se não tiver nome
- **Tipo:** Mostra `protocol_version` se for `OTHER`, senão mostra o tipo
- **Status:** Online/Offline com cores diferentes

---

## 🔐 Segurança e Confiabilidade

### Tratamento de Erros
- Timeouts em todas as operações de rede
- Retry automático quando possível
- Mensagens de erro claras para o usuário
- Logs detalhados para debugging

### Recuperação Automática
- Health check a cada 1 minuto
- Reinicialização automática do servidor
- Detecção de reconexão de rede
- Limpeza de cache em caso de erros

---

## 📝 Exemplos de Uso

### Exemplo 1: Sincronizar Dispositivo
1. Usuário abre detalhes do dispositivo
2. Edita o nome para "Lâmpada Sala"
3. Clica em "Salvar Nome e Sincronizar"
4. Tela de loading aparece
5. Sistema busca dispositivo na rede
6. Verifica se código corresponde
7. Atualiza no Supabase:
   - name: "Lâmpada Sala"
   - lan_ip: "192.168.1.100"
   - protocol_version: "3.3"
8. Retorna com sucesso

### Exemplo 2: Recuperação após Queda de Internet
1. Internet cai por 30+ minutos
2. Servidor continua tentando operar (com timeouts)
3. Internet volta
4. `NetworkChangeReceiver` detecta reconexão
5. Aguarda 5 segundos
6. Verifica se servidor responde
7. Se não responder, reinicia automaticamente
8. Servidor volta a funcionar normalmente

---

## 🚀 Benefícios

1. **Confiabilidade:** Servidor se recupera automaticamente após quedas de internet
2. **Sincronização:** Dados sempre atualizados no Supabase
3. **Usabilidade:** Interface clara e intuitiva
4. **Automação:** Menos intervenção manual necessária
5. **Visibilidade:** Protocol version visível na lista de dispositivos

---

## 📋 Resumo das Mudanças

| Componente | Mudança |
|------------|---------|
| **NetworkChangeReceiver** | Novo - Detecta mudanças de rede |
| **PythonServerService** | Health check periódico adicionado |
| **tuya_server.py** | Integração Supabase + sincronização |
| **DeviceDetailsActivity** | Campo de edição + sincronização |
| **LoadingSyncActivity** | Novo - Tela de loading |
| **DeviceAdapter** | Mostra protocol_version |
| **TuyaDevice** | Campo protocolVersion adicionado |

---

## 🔄 Compatibilidade

- **Android:** MinSdk 24 (Android 7.0)
- **TargetSdk:** 34 (Android 14)
- **Python:** 3.11 (via Chaquopy)
- **Supabase:** Compatível com API REST

---

## 📞 Suporte

Para problemas ou dúvidas, verifique:
- Logs do servidor Python
- Logs do Android (tag: "PythonServerService", "NetworkChangeReceiver", "LoadingSync")
- Status do workflow no GitHub Actions



