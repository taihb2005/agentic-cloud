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
