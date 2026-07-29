#!/usr/bin/env python3
"""Stub da TibiaData API para o CI (e para rodar os testes de navegação offline).

Por que existe: o suíte de navegação reprova qualquer requisição 4xx/5xx, e a home
chama `GET /api/worlds`, que no backend vira uma chamada de verdade à
api.tibiadata.com. Com isso, o job do CI dependeria de uma API pública, de fora,
estar de pé e não bloquear o IP do runner — a receita conhecida para um job que
falha sem culpa do código, é ignorado e depois desligado.

Sobe em uma porta, responde os dois caminhos que o boot e a home usam, e nada mais:

    GET /v4/worlds     → dois mundos fixos
    GET /v4/creatures  → algumas criaturas fixas
    GET /health        → o próprio stub (para o CI esperar por ele)

Qualquer outro caminho responde **404 com JSON**, de propósito: se um teste novo
passar a depender de outro endpoint da TibiaData, a falha aponta para cá em vez de
virar timeout misterioso.

Uso:
    python3 ops/ci/tibiadata-stub.py [porta]        # padrão: 8081

Depois, suba o backend com:
    TIBIADATA_BASE_URL=http://localhost:8081
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# Os nomes não importam para o teste; a estabilidade importa. Os mundos aparecem
# no filtro da home e as criaturas no seletor de criatura-alvo.
WORLDS = {"worlds": {"regular_worlds": [{"name": "Antica"}, {"name": "Refugia"}]}}

CREATURES = {
    "creatures": {
        "creature_list": [
            {"name": "Demon", "race": "demon", "image_url": ""},
            {"name": "Dragon Lord", "race": "dragonlord", "image_url": ""},
            {"name": "Rotworm", "race": "rotworm", "image_url": ""},
        ]
    }
}

RESPOSTAS = {
    "/v4/worlds": WORLDS,
    "/v4/creatures": CREATURES,
    "/health": {"status": "UP"},
}


class Stub(BaseHTTPRequestHandler):
    def do_GET(self):
        corpo = RESPOSTAS.get(self.path.split("?")[0])
        if corpo is None:
            self._responder(404, {"error": f"stub da TibiaData não conhece {self.path}"})
            return
        self._responder(200, corpo)

    def _responder(self, status, corpo):
        dados = json.dumps(corpo).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        self.wfile.write(dados)

    def log_message(self, *_):
        """Silencia o log por requisição: no CI ele só polui a saída do job."""


if __name__ == "__main__":
    porta = int(sys.argv[1]) if len(sys.argv) > 1 else 8081
    print(f"stub da TibiaData ouvindo em http://localhost:{porta}", flush=True)
    HTTPServer(("127.0.0.1", porta), Stub).serve_forever()
