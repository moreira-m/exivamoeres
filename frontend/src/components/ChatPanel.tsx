import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useChat } from '../hooks/useChat'
import { Card } from './ui/Card'
import { Button } from './ui/Button'
import { getApiErrorMessage } from '../lib/apiError'

interface Props {
  listId: number
  /** Personagem (membro ativo) com o qual o usuário fala neste time. */
  actingCharacterId: number
}

export function ChatPanel({ listId, actingCharacterId }: Props) {
  const { t } = useTranslation()
  const { messages, send } = useChat(listId)
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const content = text.trim()
    if (!content) return
    setError('')
    try {
      await send(actingCharacterId, content)
      setText('')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="flex h-96 flex-col p-4">
      <h3 className="mb-2 text-lg text-ink">{t('chat.title')}</h3>
      <div className="flex-1 space-y-2 overflow-y-auto border-2 border-ink/20 bg-canvas p-2">
        {messages.length === 0 && (
          <p className="text-sm font-bold text-ink/50">{t('chat.empty')}</p>
        )}
        {messages.map((m) => (
          <div key={m.id} className="text-sm text-ink">
            <span className="font-black text-primary">{m.characterName}</span>{' '}
            <span>{m.content}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <form onSubmit={submit} className="mt-3 flex gap-2">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={t('chat.placeholder')}
          maxLength={1000}
          className="flex-1 border-[3px] border-ink bg-surface px-3 py-2 font-mono text-ink outline-none"
        />
        <Button type="submit" variant="accent">
          {t('chat.send')}
        </Button>
      </form>
      {error && <p className="mt-2 text-sm font-bold text-accent">{error}</p>}
    </Card>
  )
}
