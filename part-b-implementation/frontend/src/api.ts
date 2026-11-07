// Typed client for the clinic-app API. Same origin in the Docker image; proxied by Vite in dev.

export type Role = 'CLINIC_MANAGER' | 'RECEPTION' | 'MEDICAL_STAFF'
export type DepartmentStatus = 'NORMAL' | 'WARNING' | 'OVERLOADED' | 'CRITICAL'
export type RoomStatus = 'AVAILABLE' | 'OCCUPIED' | 'INACTIVE' | 'MAINTENANCE'
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL'

export interface Session {
  token: string
  expiresAt: string
  email: string
  displayName: string
  role: Role
}

export interface Room { id: number; departmentId: number; name: string; status: RoomStatus }
export interface Doctor { id: number; departmentId: number; name: string; available: boolean; maxConcurrentPatients: number }
export interface Medication { id: number; name: string; stock: number; threshold: number; belowThreshold: boolean }
export interface Alert {
  id: number; type: string; severity: AlertSeverity; departmentId: number | null; message: string
  active: boolean; createdBy: 'AI' | 'USER' | 'SYSTEM'; createdAt: string; resolvedAt: string | null
}
export interface DepartmentState {
  id: number; name: string; status: DepartmentStatus; maxQueue: number
  waitingCount: number; inTreatmentCount: number; avgWaitMinutes: number; maxWaitMinutes: number
  roomsAvailable: number; roomsOccupied: number; roomsInactive: number; roomsMaintenance: number
  doctorsAvailable: number; doctorsTotal: number; rooms: Room[]; doctors: Doctor[]
}
export interface ClinicState {
  generatedAt: string; departments: DepartmentState[]; medications: Medication[]; activeAlerts: Alert[]
}
export interface StaffAccount {
  id: number; email: string; displayName: string; role: Role; active: boolean; createdAt: string
}

export const ROLE_LABELS: Record<Role, string> = {
  CLINIC_MANAGER: 'Clinic manager',
  RECEPTION: 'Reception',
  MEDICAL_STAFF: 'Medical staff',
}

/** Thrown for any non-2xx answer; `status` 401 means the session is over. */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    let message = response.statusText || `HTTP ${response.status}`
    try {
      const body = await response.json()
      message = body.detail || body.message || body.title || message
    } catch {
      // not JSON; keep the status text
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  login: (email: string, password: string) =>
    request<Session>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  clinicState: (token: string) => request<ClinicState>('/api/clinic/state', {}, token),
  listStaff: (token: string) => request<StaffAccount[]>('/api/users', {}, token),
  createStaff: (token: string, body: { email: string; displayName: string; password: string; role: Role }) =>
    request<StaffAccount>('/api/users', { method: 'POST', body: JSON.stringify(body) }, token),
  updateStaff: (token: string, id: number,
                body: Partial<{ displayName: string; role: Role; active: boolean; password: string }>) =>
    request<StaffAccount>(`/api/users/${id}`, { method: 'PATCH', body: JSON.stringify(body) }, token),
  changeOwnPassword: (token: string, currentPassword: string, newPassword: string) =>
    request<void>('/api/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }, token),
}
