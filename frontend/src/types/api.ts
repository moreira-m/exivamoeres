// Tipos espelhando os DTOs do backend (dto/auth e dto/claim).
// Manter sincronizado ao alterar os records Java.

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'DISCORD' | 'ANONYMOUS'
export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type SoulcoreStatus = 'OBTAINED' | 'UNLOCKED'

export interface UserResponse {
  id: number
  displayName: string
  email: string | null
  authProvider: AuthProvider
  anonymous: boolean
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: UserResponse
}

export interface ClaimResponse {
  id: number
  characterName: string
  world: string
  verificationCode: string
  status: ClaimStatus
  lastCheckedAt: string | null
  createdAt: string
  expiresAt: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  message: string
  fieldErrors: Record<string, string> | null
}
