# Instruções para Fazer Push no GitHub

## 📋 Passos para Atualizar o Repositório

### 1. Adicionar o Remote (se ainda não foi adicionado)

```bash
git remote add origin https://github.com/MRITSoftware/mrit-server.git
```

### 2. Verificar o Remote

```bash
git remote -v
```

### 3. Fazer o Commit

```bash
git commit -m "feat: Adiciona recuperação automática após queda de internet e integração com Supabase

- Adiciona NetworkChangeReceiver para detectar mudanças de conectividade
- Implementa health check periódico no PythonServerService
- Adiciona integração completa com Supabase
- Cria endpoint /tuya/sync para sincronizar devices
- Melhora tratamento de erros com timeouts em operações de rede
- Adiciona suporte para atualização automática de lan_ip, protocol_version, name e local_key"
```

### 4. Fazer Push para o Repositório

```bash
# Se for a primeira vez (branch main ainda não existe no remoto)
git push -u origin main

# Ou se a branch já existe
git push origin main
```

### 5. Verificar o Workflow no GitHub

Após o push:
1. Acesse: https://github.com/MRITSoftware/mrit-server
2. Vá para a aba **Actions**
3. Aguarde o workflow "Build APK" completar
4. Baixe o APK gerado na seção **Artifacts**

## 🔄 Se o Repositório Já Tiver Conteúdo

Se o repositório remoto já tiver commits, você pode precisar fazer pull primeiro:

```bash
# Buscar mudanças do remoto
git fetch origin

# Fazer merge (se necessário)
git merge origin/main --allow-unrelated-histories

# Ou fazer rebase
git rebase origin/main
```

## ⚠️ Resolver Conflitos (se houver)

Se houver conflitos durante o merge:

```bash
# Resolver conflitos manualmente nos arquivos
# Depois:
git add .
git commit -m "Resolve conflitos de merge"
git push origin main
```

## 📝 Verificar Status

```bash
# Ver status atual
git status

# Ver histórico de commits
git log --oneline

# Ver diferenças
git diff
```


