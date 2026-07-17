import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { useClaims, useCreateClaim } from '../../hooks/useClaims'
import { getApiErrorMessage } from '../../lib/apiError'
import type { ClaimStatus } from '../../types/api'

const statusTone: Record<ClaimStatus, 'primary' | 'accent' | 'muted'> = {
  APPROVED: 'primary',
  PENDING: 'accent',
  REJECTED: 'muted',
}

/**
 * Aba "Configuração de personagem": inicia o claim (verificação via hash no
 * campo Comment do Tibia.com). A checagem é automática (job de polling).
 */
export function CharactersPage() {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const claims = useClaims()
  const createClaim = useCreateClaim()

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
        {t('characters.title')}
      </h1>

      <Card className="mb-6 p-5">
        <h2 className="mb-1 text-xl text-ink">{t('characters.verifyTitle')}</h2>
        <p className="mb-4 text-sm font-bold text-ink/70">{t('characters.verifyHelp')}</p>
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3 [&_span]:text-ink">
          <div className="min-w-[240px] flex-1">
            <Input
              label={t('characters.characterName')}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('characters.characterNamePlaceholder')}
              required
            />
          </div>
          <Button type="submit" disabled={createClaim.isPending}>
            {t('characters.startVerification')}
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
                  <h3 className="text-lg text-ink">
                    {claim.characterName}
                    {claim.level != null && <span className="text-ink/60"> ({claim.level})</span>}
                  </h3>
                  <Badge tone={statusTone[claim.status]}>
                    {t(`enums.claimStatus.${claim.status}`)}
                  </Badge>
                </div>
                <p className="text-sm font-bold text-ink/70">
                  {t('characters.world')}: {claim.world}
                </p>
                {claim.status === 'PENDING' && (
                  <>
                    <p className="mt-1 text-sm text-ink">
                      {t('characters.pasteCode')}{' '}
                      <code className="border-2 border-ink bg-canvas px-2 py-0.5 font-mono font-bold text-accent">
                        {claim.verificationCode}
                      </code>
                    </p>
                    <p className="mt-1 text-xs font-bold text-ink/60">
                      {t('characters.autoVerifyHint')}
                    </p>
                  </>
                )}
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">{t('characters.empty')}</Card>
      )}
    </Layout>
  )
}
