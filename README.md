# FF PANEL — ZIP para AppsGeyser

## Como gerar o APK (AppsGeyser)
1. Selecione TODOS os arquivos/pastas deste ZIP e compacte de novo,
   garantindo que `index.html` fique na RAIZ (não dentro de uma subpasta).
2. AppsGeyser → "HTML5 / ZIP app" → Upload ZIP.
3. Nome, ícone, e em Permissões marque INTERNET.
4. Gerar APK → instalar.

## Senha padrão
ff2026  (mude no ⚙ dentro do app)

## Fluxo
Login → PAREAR (depuração Wi-Fi) → INJETAR → círculo aparece →
clique no círculo → painel → AIM / ESP / DESINJETAR.

## Patch
Base atualizada para OB55 (atual desde 16/09/2026). Só Android, só Free Fire normal.

## Limite do AppsGeyser
AppsGeyser = WebView. O círculo aparece dentro do app, não flutuando
sobre o Free Fire. Para o círculo flutuar de verdade, use a pasta `native/`
(Android Studio / Capacitor), que já traz SYSTEM_ALERT_WINDOW,
FOREGROUND_SERVICE e POST_NOTIFICATIONS.

## Ponte ADB (opcional, para injeção real)
Termux: `pkg install python android-tools` e `python bridge/ff-bridge.py`.
Sem a ponte o app roda em modo demo (todos os botões funcionam e registram comando).