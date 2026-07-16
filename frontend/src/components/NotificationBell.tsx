import { Link } from 'react-router-dom'
import { useUnreadCount } from '../hooks/useNotifications'

/** Sino de notificações com badge de não-lidas (link para a página). */
export function NotificationBell({ onClick }: { onClick?: () => void }) {
  const { data: unread = 0 } = useUnreadCount()

  return (
    <Link
      to="/account/notifications"
      onClick={onClick}
      aria-label="Notificações"
      className="relative flex items-center justify-center border-[3px] border-ink bg-surface px-3 py-2 text-lg leading-none text-ink"
    >
      🔔
      {unread > 0 && (
        <span className="absolute -right-2 -top-2 flex h-5 min-w-[20px] items-center justify-center border-2 border-ink bg-accent px-1 text-xs font-black text-white">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  )
}
