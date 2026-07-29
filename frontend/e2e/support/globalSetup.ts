import { API_BASE } from './session'

/**
 * Porta de entrada do suíte: confere que o backend está de pé **antes** de abrir
 * navegador.
 *
 * Sem isto, backend desligado reprova todos os testes com uma parede de
 * `ERR_CONNECTION_REFUSED` — sintoma, não causa. A falha aqui diz o que fazer.
 */
export default async function globalSetup() {
  const resposta = await fetch(`${API_BASE}/actuator/health`).catch(() => null)

  if (!resposta?.ok) {
    throw new Error(
      `O backend não respondeu em ${API_BASE}.\n\n` +
        'Estes testes abrem o site de verdade: precisam do backend e do banco.\n' +
        '  cd backend && export DOCKER_HOST="unix://$HOME/.orbstack/run/docker.sock"\n' +
        '  set -a && . ../.env && set +a\n' +
        '  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run\n\n' +
        `(Outra URL? Use E2E_API_BASE. Hoje: ${API_BASE}.)`,
    )
  }
}
