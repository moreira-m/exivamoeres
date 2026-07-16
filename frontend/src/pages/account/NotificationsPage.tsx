import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { useNotifications, useMarkNotificationsRead } from '../../hooks/useNotifications'
import type { NotificationResponse } from '../../types/api'

export function NotificationsPage() {
  const { t } = useTranslation()
  const notifications = useNotifications()
  const { markOne, markAll } = useMarkNotificationsRead()

  const items = notifications.data?.content ?? []

  return (
    <Layout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">
          {t('notifications.title')}
        </h1>
        {items.some((n) => !n.read) && (
          <Button variant="neutral" onClick={() => markAll.mutate()} disabled={markAll.isPending}>
            {t('notifications.markAllRead')}
          </Button>
        )}
      </div>

      {notifications.isLoading ? (
        <Spinner />
      ) : items.length > 0 ? (
        <div className="space-y-3">
          {items.map((n) => (
            <NotificationRow key={n.id} notification={n} onRead={() => markOne.mutate(n.id)} />
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">{t('notifications.empty')}</Card>
      )}
    </Layout>
  )
}

function NotificationRow({
  notification: n,
  onRead,
}: {
  notification: NotificationResponse
  onRead: () => void
}) {
  const { t } = useTranslation()
  const message = t(`notifications.types.${n.type}`, { team: n.listName ?? '—' })
  const body = (
    <Card
      className={`flex items-center gap-3 p-4 ${n.read ? 'opacity-60' : 'ring-2 ring-primary'}`}
    >
      {!n.read && <span className="h-2.5 w-2.5 shrink-0 rounded-full bg-accent" />}
      <span className="flex-1 font-bold text-ink">{message}</span>
      {!n.read && (
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault()
            onRead()
          }}
          className="text-xs font-extrabold uppercase text-ink/60 hover:text-ink"
        >
          ✓
        </button>
      )}
    </Card>
  )
  // Notificações com time levam para a página do time ao clicar.
  return n.listId != null ? (
    <Link to={`/teams/${n.listId}`} onClick={onRead} className="block">
      {body}
    </Link>
  ) : (
    body
  )
}
