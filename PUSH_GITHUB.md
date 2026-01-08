# 🚀 Instruções para Push no GitHub

## ✅ Status Atual

- ✅ Remote configurado: `https://github.com/MRITSoftware/servidor-mrit.git`
- ✅ Arquivos adicionados ao staging
- ✅ Commit criado com todas as mudanças

## 📤 Próximo Passo: Push

Execute o comando abaixo para fazer push para o GitHub:

```bash
git push -u origin main
```

**Nota:** Se o repositório estiver vazio, use `-u` para configurar o upstream. Se já tiver conteúdo, pode precisar fazer pull primeiro.

## 🔄 Se o Repositório Já Tiver Conteúdo

Se o GitHub retornar erro de que o repositório já tem commits:

```bash
# Buscar mudanças do remoto
git fetch origin

# Fazer merge (se necessário)
git merge origin/main --allow-unrelated-histories

# Depois fazer push
git push origin main
```

## 📋 Após o Push

1. **Acesse:** https://github.com/MRITSoftware/servidor-mrit
2. **Vá para a aba Actions**
3. **Aguarde o workflow "Build APK" completar** (pode levar 10-15 minutos)
4. **Baixe o APK** na seção **Artifacts** após o build completar

## 🔍 Verificar Status

```bash
# Ver status atual
git status

# Ver histórico de commits
git log --oneline -5

# Ver remote configurado
git remote -v
```

## ⚠️ Troubleshooting

### Erro: "Updates were rejected"
```bash
git pull origin main --rebase
git push origin main
```

### Erro: "Permission denied"
- Verifique se você tem acesso ao repositório
- Verifique suas credenciais Git

### Workflow não iniciou
- Verifique se o arquivo `.github/workflows/build-apk.yml` existe
- Verifique se fez push para a branch `main` ou `master`

