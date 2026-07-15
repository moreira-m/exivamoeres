import type { ReactNode } from 'react'
import { NavBar } from './NavBar'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <>
      <NavBar />
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </>
  )
}
