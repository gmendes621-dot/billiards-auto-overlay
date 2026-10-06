# Billiards Auto Overlay

Overlay Android (MediaProjection + WebView) para testar o seu app de sinuca.

- Detecta a cor da mesa automaticamente (qualquer tema).
- Marca a bola branca (ciano), lisas (amarelo), listradas (rosa) e a 8 (cinza).
- Caçapas aproximadas pelos limites da mesa.

## Limitações conhecidas
- Bola com a mesma cor da mesa pode não ser detectada.
- Caçapas são estimadas (cantos e meio das laterais), não detectadas.
- Para precisão total, use as coordenadas direto do motor do jogo.

## Usar
Abra no Android Studio, sincronize o Gradle, rode, autorize "Exibir sobre outros apps" e a captura de tela, e abra o jogo.
