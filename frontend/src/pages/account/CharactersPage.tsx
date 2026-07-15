import { useState } from 'react'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { useClaims, useCreateClaim, useVerifyClaimNow } from '../../hooks/useClaims'
import { getApiErrorMessage } from '../../lib/apiError'
import type { ClaimStatus } from '../../types/api'

const statusTone: Record<ClaimStatus, 'primary' | 'accent' | 'muted'> = {
  APPROVED: 'primary',
  PENDING: 'accent',
  REJECTED: 'muted',
}

/**
 * Aba "Configuração de personagem": inicia o claim (verificação via hash no
 * campo Comment do Tibia.com) e permite forçar a checagem imediata.
 */
export function CharactersPage() {
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const claims = useClaims()
  const createClaim = useCreateClaim()
  const verifyNow = useVerifyClaimNow()

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await createClaim.mutateAsync(name.trim())
      setName('')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Layout>
      <h1 className="mb-6 text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">
        Meus personagens
      </h1>

      <Card className="mb-6 p-5">
        <h2 className="mb-1 text-xl text-ink">Verificar um personagem</h2>
        <p className="mb-4 text-sm font-bold text-ink/70">
          Informe o nome exato do personagem no Tibia.com. Geramos um código que você cola
          no campo <span className="text-accent">Comment</span> do personagem; verificamos
          automaticamente a cada 15 minutos (ou use “Verificar agora”).
        </p>
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3 [&_span]:text-ink">
          <div className="min-w-[240px] flex-1">
            <Input
              label="Nome do personagem"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex.: Bubble"
              required
            />
          </div>
          <Button type="submit" disabled={createClaim.isPending}>
            Iniciar verificação
          </Button>
        </form>
        {error && <p className="mt-3 font-bold text-accent">{error}</p>}
      </Card>

      {claims.isLoading ? (
        <Spinner />
      ) : claims.data && claims.data.length > 0 ? (
        <div className="space-y-4">
          {claims.data.map((claim) => (
            <Card key={claim.id} className="flex flex-wrap items-center gap-4 p-4">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="text-lg text-ink">{claim.characterName}</h3>
                  <Badge tone={statusTone[claim.status]}>{claim.status}</Badge>
                </div>
                <p className="text-sm font-bold text-ink/70">Mundo: {claim.world}</p>
                {claim.status === 'PENDING' && (
                  <p className="mt-1 text-sm text-ink">
                    Cole no Comment:{' '}
                    <code className="border-2 border-ink bg-canvas px-2 py-0.5 font-mono font-bold text-accent">
                      {claim.verificationCode}
                    </code>
                  </p>
                )}
              </div>
              {claim.status === 'PENDING' && (
                <Button
                  variant="accent"
                  onClick={() => verifyNow.mutate(claim.id)}
                  disabled={verifyNow.isPending}
                >
                  Verificar agora
                </Button>
              )}
            </Card>
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">
          Você ainda não verificou nenhum personagem.
        </Card>
      )}
    </Layout>
  )
}
