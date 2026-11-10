import type {
	ApiResponse,
	AuthResponseData,
	ConfigureShareRequest,
	NodeShareDetailResponse,
	PagedResponse,
	PresignedDownloadResponse,
	PublicAccessSessionResponse,
	PublicShareInfoResponse,
	SharedWithMeNodeResponse,
	UserQuotaResponse,
	UserResponse,
	UserSearchResponse
} from './types'

/**
 * Lấy URL của API Gateway từ biến môi trường .env (Vite / Node) hoặc fallback về http://localhost:8089.
 */
export function getEnvGatewayUrl(): string {
	// Vite client environment (import.meta.env)
	if (typeof import.meta !== 'undefined' && import.meta.env) {
		const envUrl =
			import.meta.env.VITE_API_GATEWAY_URL ||
			import.meta.env.VITE_API_URL ||
			import.meta.env.VITE_BACKEND_URL
		if (envUrl && typeof envUrl === 'string' && envUrl.trim()) {
			return envUrl.trim()
		}
	}

	// Node.js / process environment fallback
	if (typeof process !== 'undefined' && process.env) {
		const envUrl =
			process.env.VITE_API_GATEWAY_URL ||
			process.env.VITE_API_URL ||
			process.env.VITE_BACKEND_URL
		if (envUrl && typeof envUrl === 'string' && envUrl.trim()) {
			return envUrl.trim()
		}
	}

	return 'http://localhost:8089'
}

export class ApiClient {
	private baseUrl: string
	private isRefreshing = false
	private refreshSubscribers: Array<(token: string) => void> = []

	constructor(baseUrl?: string) {
		this.baseUrl = (baseUrl || getEnvGatewayUrl()).replace(/\/$/, '')
	}

	setBaseUrl(url: string) {
		this.baseUrl = url.replace(/\/$/, '')
	}

	getBaseUrl(): string {
		return this.baseUrl
	}

	getAccessToken(): string | null {
		return localStorage.getItem('agentic_cloud_access_token')
	}

	getRefreshToken(): string | null {
		return localStorage.getItem('agentic_cloud_refresh_token')
	}

	getCurrentUser(): UserResponse | null {
		const raw = localStorage.getItem('agentic_cloud_user')
		if (!raw) return null
		try {
			return JSON.parse(raw) as UserResponse
		} catch {
			return null
		}
	}

	setSession(authData: AuthResponseData): void {
		localStorage.setItem('agentic_cloud_access_token', authData.access_token)
		localStorage.setItem('agentic_cloud_refresh_token', authData.refresh_token)
		const user: Partial<UserResponse> = {
			id: authData.user_id,
			username: authData.username,
			email: authData.email,
			roles: authData.roles
		}
		localStorage.setItem('agentic_cloud_user', JSON.stringify(user))
	}

	setCurrentUser(user: UserResponse): void {
		localStorage.setItem('agentic_cloud_user', JSON.stringify(user))
	}

	clearSession(): void {
		localStorage.removeItem('agentic_cloud_access_token')
		localStorage.removeItem('agentic_cloud_refresh_token')
		localStorage.removeItem('agentic_cloud_user')
	}

	isAuthenticated(): boolean {
		return !!this.getAccessToken()
	}

	private onRefreshed(token: string) {
		this.refreshSubscribers.forEach((cb) => cb(token))
		this.refreshSubscribers = []
	}

	private addRefreshSubscriber(cb: (token: string) => void) {
		this.refreshSubscribers.push(cb)
	}

	async request<T>(endpoint: string, options: RequestInit = {}, isRetry = false): Promise<ApiResponse<T>> {
		const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
		const url = `${this.baseUrl}${cleanEndpoint}`

		const headers = new Headers(options.headers || {})
		if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
			headers.set('Content-Type', 'application/json')
		}

		const token = this.getAccessToken()
		if (token && !headers.has('Authorization')) {
			headers.set('Authorization', `Bearer ${token}`)
		}

		const response = await fetch(url, {
			...options,
			headers
		})

		// Token expired (401) handling
		if (response.status === 401 && !isRetry) {
			const refreshToken = this.getRefreshToken()
			if (refreshToken) {
				if (!this.isRefreshing) {
					this.isRefreshing = true
					try {
						const refreshRes = await this.refreshToken(refreshToken)
						if (refreshRes.success && refreshRes.data?.access_token) {
							localStorage.setItem('agentic_cloud_access_token', refreshRes.data.access_token)
							if (refreshRes.data.refresh_token) {
								localStorage.setItem('agentic_cloud_refresh_token', refreshRes.data.refresh_token)
							}
							this.onRefreshed(refreshRes.data.access_token)
						} else {
							this.clearSession()
							throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
						}
					} catch (err) {
						this.clearSession()
						throw err
					} finally {
						this.isRefreshing = false
					}
				}

				// Wait for refresh to complete
				return new Promise((resolve, reject) => {
					this.addRefreshSubscriber(async (newToken) => {
						try {
							headers.set('Authorization', `Bearer ${newToken}`)
							const retryRes = await fetch(url, { ...options, headers })
							const data = (await retryRes.json()) as ApiResponse<T>
							resolve(data)
						} catch (err) {
							reject(err)
						}
					})
				})
			}
		}

		if (!response.ok) {
			let errorBody: ApiResponse<T> | null = null
			try {
				errorBody = (await response.json()) as ApiResponse<T>
			} catch {
				// Non-json response
			}
			const message =
				errorBody?.message ||
				errorBody?.error ||
				`Yêu cầu thất bại với mã trạng thái ${response.status} (${response.statusText})`
			throw new Error(message)
		}

		return (await response.json()) as ApiResponse<T>
	}

	async get<T>(endpoint: string, query?: Record<string, string | number | boolean | undefined>, extraHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
		let path = endpoint
		if (query) {
			const params = new URLSearchParams()
			Object.entries(query).forEach(([key, value]) => {
				if (value !== undefined && value !== null && value !== '') {
					params.append(key, String(value))
				}
			})
			const qs = params.toString()
			if (qs) {
				path += (path.includes('?') ? '&' : '?') + qs
			}
		}
		return this.request<T>(path, {
			method: 'GET',
			headers: extraHeaders ? new Headers(extraHeaders) : undefined
		})
	}

	async post<T>(endpoint: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			method: 'POST',
			body: body ? JSON.stringify(body) : undefined,
			headers: extraHeaders ? new Headers(extraHeaders) : undefined
		})
	}

	async put<T>(endpoint: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			method: 'PUT',
			body: body ? JSON.stringify(body) : undefined,
			headers: extraHeaders ? new Headers(extraHeaders) : undefined
		})
	}

	async patch<T>(endpoint: string, body?: unknown): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			method: 'PATCH',
			body: body ? JSON.stringify(body) : undefined
		})
	}

	async delete<T>(endpoint: string, query?: Record<string, string | number | boolean | undefined>): Promise<ApiResponse<T>> {
		let path = endpoint
		if (query) {
			const params = new URLSearchParams()
			Object.entries(query).forEach(([key, value]) => {
				if (value !== undefined && value !== null && value !== '') {
					params.append(key, String(value))
				}
			})
			const qs = params.toString()
			if (qs) {
				path += (path.includes('?') ? '&' : '?') + qs
			}
		}
		return this.request<T>(path, { method: 'DELETE' })
	}

	// ==================== User Service APIs ====================

	async login(username: string, password: string): Promise<ApiResponse<AuthResponseData>> {
		const res = await this.post<AuthResponseData>('/api/v1/user-service/auth/login', { username, password })
		if (res.success && res.data) {
			this.setSession(res.data)
		}
		return res
	}

	async register(username: string, email: string, password: string): Promise<ApiResponse<AuthResponseData>> {
		const res = await this.post<AuthResponseData>('/api/v1/user-service/auth/register', { username, email, password })
		if (res.success && res.data) {
			this.setSession(res.data)
		}
		return res
	}

	async refreshToken(refreshToken: string): Promise<ApiResponse<AuthResponseData>> {
		return this.post<AuthResponseData>('/api/v1/user-service/auth/refresh', { refresh_token: refreshToken })
	}

	async getMe(): Promise<ApiResponse<UserResponse>> {
		const res = await this.get<UserResponse>('/api/v1/user-service/users/me')
		if (res.success && res.data) {
			this.setCurrentUser(res.data)
		}
		return res
	}

	async searchUsers(query: string, limit = 10): Promise<ApiResponse<UserSearchResponse[]>> {
		return this.get<UserSearchResponse[]>('/api/v1/user-service/users/search', { q: query, limit })
	}

	// ==================== Storage Service APIs ====================

	async getUserQuota(userId: string): Promise<ApiResponse<UserQuotaResponse>> {
		return this.get<UserQuotaResponse>(`/api/v1/storage-service/storage/users/quota/${userId}`)
	}

	// ==================== Node Sharing & Permissions APIs ====================

	async configureShare(nodeId: number | string, data: ConfigureShareRequest): Promise<ApiResponse<NodeShareDetailResponse>> {
		return this.post<NodeShareDetailResponse>(`/api/v1/storage-service/nodes/${nodeId}/share`, data)
	}

	async getShareDetail(nodeId: number | string): Promise<ApiResponse<NodeShareDetailResponse>> {
		return this.get<NodeShareDetailResponse>(`/api/v1/storage-service/nodes/${nodeId}/share`)
	}

	async revokeShare(nodeId: number | string): Promise<ApiResponse<void>> {
		return this.delete<void>(`/api/v1/storage-service/nodes/${nodeId}/share`)
	}

	async updateUserGroupShare(
		nodeId: number | string,
		data: { user_ids: string[]; permission: 'VIEW' | 'EDIT' }
	): Promise<ApiResponse<NodeShareDetailResponse>> {
		return this.put<NodeShareDetailResponse>(`/api/v1/storage-service/nodes/${nodeId}/share/users`, data)
	}

	async revokeUserShare(nodeId: number | string, userId: string): Promise<ApiResponse<void>> {
		return this.delete<void>(`/api/v1/storage-service/nodes/${nodeId}/share/users/${userId}`)
	}

	async getSharedWithMe(params?: {
		nodeType?: 'FILE' | 'FOLDER'
		permission?: 'VIEW' | 'EDIT'
		name?: string
		page?: number
		size?: number
	}): Promise<ApiResponse<PagedResponse<SharedWithMeNodeResponse>>> {
		return this.get<PagedResponse<SharedWithMeNodeResponse>>('/api/v1/storage-service/nodes/shared-with-me', {
			node_type: params?.nodeType,
			permission: params?.permission,
			name: params?.name,
			page: params?.page ?? 0,
			size: params?.size ?? 50
		})
	}

	async unlockNode(nodeId: number | string, password: string): Promise<ApiResponse<{ token?: string }>> {
		return this.post<{ token?: string }>(`/api/v1/storage-service/nodes/${nodeId}/unlock`, { password })
	}

	// ==================== Public Link APIs ====================

	async getPublicShareInfo(shareToken: string): Promise<ApiResponse<PublicShareInfoResponse>> {
		return this.get<PublicShareInfoResponse>(`/api/v1/storage-service/shares/public/${shareToken}`)
	}

	async accessPublicShare(shareToken: string, password?: string): Promise<ApiResponse<PublicAccessSessionResponse>> {
		return this.post<PublicAccessSessionResponse>(
			`/api/v1/storage-service/shares/public/${shareToken}/access`,
			password ? { password } : {}
		)
	}

	async getPublicShareNodes(
		shareToken: string,
		parentId?: string,
		shareAccessToken?: string
	): Promise<ApiResponse<PagedResponse<import('./types').NodeResponse>>> {
		const headers: Record<string, string> = {}
		if (shareAccessToken) {
			headers['X-Share-Access-Token'] = shareAccessToken
		}
		return this.get<PagedResponse<import('./types').NodeResponse>>(
			`/api/v1/storage-service/shares/public/${shareToken}/nodes`,
			parentId ? { parent_id: parentId } : undefined,
			headers
		)
	}

	async getPublicDownloadUrl(
		shareToken: string,
		nodeId: number | string,
		shareAccessToken?: string
	): Promise<ApiResponse<PresignedDownloadResponse>> {
		const headers: Record<string, string> = {
			'Content-Disposition': 'attachment',
			'X-Download': 'true'
		}
		if (shareAccessToken) {
			headers['X-Share-Access-Token'] = shareAccessToken
		}
		return this.post<PresignedDownloadResponse>(
			`/api/v1/storage-service/shares/public/${shareToken}/download-url`,
			{ node_id: Number(nodeId) },
			headers
		)
	}
}

export const apiClient = new ApiClient()
