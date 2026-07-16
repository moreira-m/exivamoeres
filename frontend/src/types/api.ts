// Tipos espelhando os DTOs do backend (dto/*). Manter sincronizado ao alterar
// os records Java.

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'DISCORD' | 'ANONYMOUS'
export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type SoulcoreStatus = 'OBTAINED' | 'UNLOCKED'
export type JoinPolicy = 'MANUAL_APPROVAL' | 'AUTO_ACCEPT'
export type MembershipStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type Plan = 'FREE' | 'PREMIUM'
export type TeamStatus = 'ACTIVE' | 'COMPLETED' | 'ARCHIVED' | 'CLOSED'
export type NotificationType =
  | 'JOIN_REQUEST_RECEIVED'
  | 'JOIN_REQUEST_APPROVED'
  | 'JOIN_REQUEST_REJECTED'
  | 'KICKED_FROM_TEAM'
  | 'TEAM_DELETED'
  | 'MEMBER_LEFT'

export interface UserResponse {
  id: number
  displayName: string
  email: string | null
  authProvider: AuthProvider
  anonymous: boolean
  plan: Plan
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

export interface CharacterSummaryResponse {
  id: number
  name: string
  world: string
  vocation: string | null
}

export interface CreatureResponse {
  id: number
  name: string
  // Estrelas do Bestiary (1–5); nula para as criaturas importadas da TibiaData.
  difficulty: number | null
  imageUrl: string | null
}

export interface MembershipResponse {
  id: number
  userId: number
  characterId: number
  characterName: string
  vocation: string | null
  level: number | null
  status: MembershipStatus
  active: boolean
  joinedAt: string
}

export interface ListSummaryResponse {
  id: number
  name: string
  world: string
  shareCode: string
  targetCreatureId: number
  targetCreatureName: string
  targetCreatureImageUrl: string | null
  joinPolicy: JoinPolicy
  status: TeamStatus
  expiresAt: string
  minimumLevel: number | null
  pricePerSlot: number | null
  featured: boolean
  memberCount: number
  maxMembers: number
  hasOpenSlots: boolean
  createdAt: string
}

export interface NotificationResponse {
  id: number
  type: NotificationType
  listId: number | null
  listName: string | null
  read: boolean
  createdAt: string
}

export interface ListDetailResponse {
  summary: ListSummaryResponse
  ownerId: number
  members: MembershipResponse[]
}

export interface ListSoulcoreResponse {
  id: number
  creatureId: number
  creatureName: string
  status: SoulcoreStatus
  obtainedByCharacterId: number | null
  obtainedByCharacterName: string | null
  createdAt: string
  updatedAt: string
}

export interface CharacterSoulcoreResponse {
  creatureId: number
  creatureName: string
  unlockedAt: string
}

export interface SuggestionResponse {
  id: number
  creatureId: number
  creatureName: string
  difficulty: number | null
  reason: string
  createdAt: string
}

export interface ChatMessageResponse {
  id: number
  listId: number
  senderId: number
  senderDisplayName: string
  characterId: number
  characterName: string
  content: string
  sentAt: string
}

// Envelope de página do Spring Data (subconjunto usado no frontend).
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  message: string
  fieldErrors: Record<string, string> | null
}
