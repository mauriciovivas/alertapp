# Alerta PP

App Android leve que monitora as promoções do Passageiro de Primeira e avisa quando o título de uma nova matéria contém suas palavras-chave.

## Gerar o APK (GitHub Actions, sem instalar nada)

1. Crie uma conta gratuita em github.com e um repositório novo (pode ser privado).
2. Envie **todos os arquivos deste projeto** para o repositório, incluindo a pasta oculta `.github`.
   Opção fácil: no repositório, *Add file → Upload files* e arraste o conteúdo da pasta extraída.
3. Abra a aba **Actions**. O workflow **Build APK** roda sozinho a cada envio
   (ou clique em *Run workflow*). Leva cerca de 3 a 5 minutos.
4. Ao terminar, abra a execução e baixe o artefato **AlertaPP-apk** (um .zip com o `app-release.apk`).
5. Copie o APK para o celular e instale (permita "instalar apps desconhecidos" se pedir).

## Como funciona

- Verificação periódica via WorkManager (padrão 60 min, mínimo do Android: 15 min).
- Lê o feed RSS do site (`.../feed/`, com paginação); se falhar, lê o HTML da página.
- Palavras-chave sem distinção de maiúsculas e acentos ("bônus" = "bonus").
- Na primeira verificação o app apenas memoriza as matérias existentes (não notifica tudo de uma vez).
  Depois disso, cada matéria nova com palavra-chave gera uma notificação que abre o link.
- Tela principal: lista dos últimos N dias (padrão 5), com destaque nas matérias com palavra-chave.
  Puxe a lista para baixo para atualizar. Toque em "Configurações" para editar URL, intervalo, dias e palavras-chave.
- Tema claro/escuro automático.

## Observações

- O Android pode atrasar verificações em segundo plano com economia de bateria ativa.
  Se as notificações atrasarem, desative a otimização de bateria para o app.
- Se o site bloquear acessos automáticos, o app mostrará o erro na tela de status.
