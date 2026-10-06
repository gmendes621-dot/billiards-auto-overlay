# Billiards Auto Overlay

Overlay Android (MediaProjection + WebView) para testar o seu app de sinuca.

- Detecta a cor da mesa automaticamente (qualquer tema).
- Marca a bola branca (ciano), lisas (amarelo), listradas (rosa) e a 8 (cinza).
- **Detecta o taco** e desenha a previsão da tacada:
  - linha branca tracejada: caminho da branca (com até 2 tabelas) até a primeira bola;
  - círculo branco: onde a branca fica no momento do contato;
  - linha colorida: para onde vai a bola atingida;
  - linha ciano tracejada: desvio da branca após o contato;
  - anel verde "caçapa!" se a bola alvo entra; anel vermelho se a branca cai.
- O texto no canto resume a previsão.

## Como a previsão funciona
Física ideal: colisão elástica entre bolas iguais, sem efeito, sem atrito, força suficiente.
A bola atingida sai pela linha dos centros; a branca sai pela tangente (regra dos 90°).
Se o seu jogo tem atrito/efeito, compare a previsão com o resultado real: a diferença mostra o que ajustar no motor.

## Limitações conhecidas
- O taco precisa estar visível, ter >= 3px de espessura (na imagem reduzida a 800px) e >= 5 raios de bola dentro da mesa.
- Se a linha sair para o lado errado (atrás do taco), mude `INVERT_AIM` para `true` em `assets/index.html`.
- Bola com a mesma cor da mesa pode não ser detectada.
- Caçapas são estimadas (cantos e meio das laterais), não detectadas.
- Para precisão total, use as coordenadas direto do motor do jogo.

## Usar
Abra no Android Studio, sincronize o Gradle, rode, autorize "Exibir sobre outros apps" e a captura de tela, e abra o jogo.
