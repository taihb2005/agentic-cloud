export interface ApiResponse<T = unknown> {
	success: boolean
	message?: string
	code: number
	data: T
	error?: string
	timestamp?: string
}

export interface PagedResponse<T = unknown> {
	content: T[]
	page: number
	size: number
	total_elements: number
	total_pages: number
	last: boolean
}

export interface NodeResponse {
	id: number | string
	parent_id?: number | string | null
	path?: string
	name: string
	is_folder: boolean
	node_status?: string
	mime_type?: string | null
	size_bytes?: number | null
	etag?: string | null
	file_hash?: string | null
	is_starred?: boolean
	created_at?: string
	updated_at?: string
	deleted_at?: string | null
}

export interface UploadUrlItemResponse {
	node_id: string | number
	url: string
	instant_uploaded: boolean
	expires_at: string
}

export interface UploadUrlsResponse {
	presigned_urls: UploadUrlItemResponse[]
}

export interface DownloadUrlItemResponse {
	node_id: string | number
	file_name: string
	url: string
	expires_at: string
	mime_type?: string
	size_bytes?: number
}

export interface DownloadUrlsResponse {
	download_urls: DownloadUrlItemResponse[]
}

export interface UserQuotaResponse {
	user_id: string
	storage_id: string
	current_quota: number
	max_quota: number
	unit: string
	usage_percentage: number
	status: string
}

export interface UserStorageResponse {
	user_id: string
	storage_prefix: string
	storage_bucket_name: string
	storage_max_quota_bytes: number
	storage_current_quota_bytes: number
	root_node_id: number | string
	root_node_name: string
	root_node_path: string
}

export interface AuthResponseData {
	access_token: string
	refresh_token: string
	token_type: string
	user_id: string
	username: string
	email: string
	roles?: string[]
}

export interface UserResponse {
	id: string
	username: string
	email: string
	roles?: string[]
	storage?: UserStorageResponse
	enabled?: boolean
	created_at?: string
}

export interface UserSearchResponse {
	id: string
	username: string
	email: string
	created_at?: string
}

export interface SharedUserItemResponse {
	user_id: string
	username: string
	email?: string
	permission?: string
	shared_at?: string
}

export interface ConfigureShareRequest {
	share_mode: 'PUBLIC_LINK' | 'USER_GROUP' | 'PRIVATE'
	permission: 'VIEW' | 'EDIT'
	user_ids?: string[]
	password?: string
	expires_at?: string
	max_access_count?: number
	override_existing?: boolean
}

export interface NodeShareDetailResponse {
	node_id: number | string
	node_name: string
	is_folder: boolean
	share_mode: 'PUBLIC_LINK' | 'USER_GROUP' | 'PRIVATE'
	permission: 'VIEW' | 'EDIT'
	is_password_protected?: boolean
	shared_url?: string
	share_token?: string
	expires_at?: string
	max_access_count?: number
	access_count?: number
	status?: string
	users?: SharedUserItemResponse[]
	created_at?: string
	updated_at?: string
}

export interface SharedWithMeNodeResponse {
	node_id: number | string
	name: string
	is_folder: boolean
	mime_type?: string
	size_bytes?: number
	permission: 'VIEW' | 'EDIT'
	is_password_protected?: boolean
	is_unlocked?: boolean
	owner?: {
		id: string
		username: string
		email: string
	}
	shared_at?: string
}

export interface PublicShareInfoResponse {
	node_id?: number | string
	node_name: string
	is_folder: boolean
	size_bytes?: number
	mime_type?: string
	permission: 'VIEW' | 'EDIT'
	is_password_protected?: boolean
	owner_name?: string
	expires_at?: string
}

export interface PublicAccessSessionResponse {
	access_token: string
	expires_at?: string
}

export interface PresignedDownloadResponse {
	node_id: number | string
	file_name: string
	url: string
	expires_at: string
	mime_type?: string
	size_bytes?: number
}
