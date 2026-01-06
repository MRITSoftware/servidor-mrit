# Changelog - Atualizações do Servidor Tuya

## [2024] - Melhorias e Integração com Supabase

### ✨ Novas Funcionalidades

#### 1. **Recuperação Automática após Queda de Internet**
- ✅ Adicionado `NetworkChangeReceiver` para detectar mudanças de conectividade
- ✅ Health check periódico (a cada 1 minuto) no `PythonServerService`
- ✅ Reinicialização automática do servidor quando a internet volta
- ✅ Melhor tratamento de erros com timeouts em operações de rede

#### 2. **Integração com Supabase**
- ✅ Integração completa com banco de dados Supabase
- ✅ Sincronização automática de devices Tuya encontrados na rede
- ✅ Endpoint `/tuya/sync` para sincronizar devices com a tabela `tuya_devices`
- ✅ Atualização automática de `lan_ip`, `protocol_version`, `name` e `local_key`
- ✅ Configuração automática do Supabase na inicialização

#### 3. **Melhorias no Servidor Python**
- ✅ Timeout de 30 segundos em operações de rede para evitar travamentos
- ✅ Limpeza automática de cache quando há erros de conexão
- ✅ Tratamento robusto de erros em todas as operações de rede

### 🔧 Mudanças Técnicas

#### Dependências Adicionadas
- `supabase` (Python) - Para integração com banco de dados
- Mantido `flask` e `tinytuya`

#### Novos Arquivos
- `app/src/main/java/com/mritsoftware/mritserver/receiver/NetworkChangeReceiver.kt`
  - Detecta mudanças de conectividade de rede
  - Verifica e reinicia servidor quando necessário

#### Arquivos Modificados
- `app/src/main/python/tuya_server.py`
  - Adicionado suporte ao Supabase
  - Funções de sincronização com banco de dados
  - Endpoints de configuração e sincronização
  - Melhorias em timeouts e tratamento de erros

- `app/src/main/java/com/mritsoftware/mritserver/service/PythonServerService.kt`
  - Health check periódico
  - Reinicialização automática do servidor
  - Melhor gerenciamento de ciclo de vida

- `app/src/main/AndroidManifest.xml`
  - Registrado `NetworkChangeReceiver` para eventos de conectividade

- `app/build.gradle`
  - Adicionada dependência `supabase` no Python

### 📝 Novos Endpoints da API

#### `POST /tuya/sync`
Sincroniza devices encontrados na rede LAN com a tabela `tuya_devices` no Supabase.

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

#### `POST /config/supabase`
Configura as credenciais do Supabase.

**Body:**
```json
{
  "url": "https://kihyhoqbrkwbfudttevo.supabase.co",
  "anon_key": "sua_anon_key"
}
```

### 🔐 Configuração do Supabase

O servidor está pré-configurado com as credenciais do Supabase:
- **URL:** `https://kihyhoqbrkwbfudttevo.supabase.co`
- **Anon Key:** Configurada automaticamente

As credenciais são salvas em `config.json` e podem ser atualizadas via API.

### 🐛 Correções

- Corrigido problema de travamento quando a internet cai por mais de 30 minutos
- Melhorado tratamento de timeouts em operações de rede
- Cache de IP limpo automaticamente em caso de erros

### 📚 Documentação

- Adicionado `CHANGELOG.md` com todas as mudanças
- Comentários melhorados no código
- Logs mais informativos para debugging


