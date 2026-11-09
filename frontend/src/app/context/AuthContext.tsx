import React, { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react'
import { apiClient, ApiClient } from '../../adapters/agentic-cloud/api-client'
import { AgenticCloudStorageAdapter } from '../../adapters/agentic-cloud/agentic-adapter'
import type { UserQuotaResponse, UserResponse } from '../../adapters/agentic-cloud/types'

interface AuthContextType {
	client: ApiClient
	user: UserResponse | null
	quota: UserQuotaResponse | null
	adapter: AgenticCloudStorageAdapter | null
	isAuthenticated: boolean
	isLoading: boolean
	error: string | null
	login: (username: string, password: string) => Promise<void>
	register: (username: string, email: string, password: string) => Promise<void>
	logout: () => void
	refreshQuota: () => Promise<void>
	refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode; gatewayUrl?: string }> = ({
	children,
	gatewayUrl
}) => {
	const [client] = useState(() => (gatewayUrl ? new ApiClient(gatewayUrl) : apiClient))
	const [user, setUser] = useState<UserResponse | null>(() => client.getCurrentUser())
	const [quota, setQuota] = useState<UserQuotaResponse | null>(null)
	const [isLoading, setIsLoading] = useState<boolean>(true)
	const [error, setError] = useState<string | null>(null)

	const adapter = useMemo(() => {
		if (!user) return null
		return new AgenticCloudStorageAdapter({
			client,
			rootNodeId: user.storage?.root_node_id
		})
	}, [user, client])

	const refreshQuota = useCallback(async () => {
		if (!user?.id) return
		try {
			const res = await client.getUserQuota(user.id)
			if (res.success && res.data) {
				setQuota(res.data)
			}
		} catch (err: unknown) {
			console.error('Lỗi lấy quota:', err)
		}
	}, [client, user?.id])

	const refreshUser = useCallback(async () => {
		if (!client.isAuthenticated()) {
			setUser(null)
			setIsLoading(false)
			return
		}
		try {
			setIsLoading(true)
			setError(null)
			const res = await client.getMe()
			if (res.success && res.data) {
				setUser(res.data)
				if (res.data.id) {
					try {
						const qRes = await client.getUserQuota(res.data.id)
						if (qRes.success && qRes.data) {
							setQuota(qRes.data)
						}
					} catch {
						// quota optional
					}
				}
			} else {
				client.clearSession()
				setUser(null)
			}
		} catch (err: unknown) {
			console.warn('Không thể nạp thông tin user:', err)
			client.clearSession()
			setUser(null)
		} finally {
			setIsLoading(false)
		}
	}, [client])

	useEffect(() => {
		refreshUser()
	}, [refreshUser])

	const login = async (username: string, password: string) => {
		setError(null)
		setIsLoading(true)
		try {
			const res = await client.login(username, password)
			if (res.success) {
				await refreshUser()
			} else {
				throw new Error(res.message || 'Đăng nhập không thành công')
			}
		} catch (err: unknown) {
			const msg = err instanceof Error ? err.message : 'Đăng nhập thất bại'
			setError(msg)
			throw err
		} finally {
			setIsLoading(false)
		}
	}

	const register = async (username: string, email: string, password: string) => {
		setError(null)
		setIsLoading(true)
		try {
			const res = await client.register(username, email, password)
			if (res.success) {
				await refreshUser()
			} else {
				throw new Error(res.message || 'Đăng ký không thành công')
			}
		} catch (err: unknown) {
			const msg = err instanceof Error ? err.message : 'Đăng ký thất bại'
			setError(msg)
			throw err
		} finally {
			setIsLoading(false)
		}
	}

	const logout = () => {
		client.clearSession()
		setUser(null)
		setQuota(null)
		setError(null)
	}

	return (
		<AuthContext.Provider
			value={{
				client,
				user,
				quota,
				adapter,
				isAuthenticated: !!user,
				isLoading,
				error,
				login,
				register,
				logout,
				refreshQuota,
				refreshUser
			}}
		>
			{children}
		</AuthContext.Provider>
	)
}

export function useAuth() {
	const context = useContext(AuthContext)
	if (!context) {
		throw new Error('useAuth must be used within an AuthProvider')
	}
	return context
}
