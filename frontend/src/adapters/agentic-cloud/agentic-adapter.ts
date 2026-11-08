import {
	FileBrowserAdapterError,
	type FileBrowserAdapter,
	type FileBrowserListOptions,
	type FileBrowserListResult,
	type FileBrowserUploadOptions,
	type FileNode
} from '../../core/types'
import {
	getFileBrowserBasename,
	getFileBrowserDirname,
	joinFileBrowserPath,
	normalizeFileBrowserPath,
	ROOT_PATH
} from '../../core/path'
import { ApiClient, apiClient as defaultApiClient } from './api-client'
import { calculateSha256 } from './hash'
import type {
	DownloadUrlsResponse,
	NodeResponse,
	PagedResponse,
	UploadUrlsResponse
} from './types'

export interface AgenticCloudAdapterOptions {
	client?: ApiClient
	rootNodeId?: string | number
}

export class AgenticCloudStorageAdapter implements FileBrowserAdapter<Record<string, unknown>> {
	private readonly client: ApiClient
	private rootNodeId: string | null = null
	private readonly pathToId = new Map<string, string>()
	private readonly idToPath = new Map<string, string>()

	constructor(options: AgenticCloudAdapterOptions = {}) {
		this.client = options.client || defaultApiClient
		if (options.rootNodeId) {
			this.setRootNodeId(String(options.rootNodeId))
		}
	}

	setRootNodeId(id: string): void {
		this.rootNodeId = id
		this.pathToId.set(ROOT_PATH, id)
		this.idToPath.set(id, ROOT_PATH)
	}

	getRootNodeId(): string | null {
		return this.rootNodeId
	}

	private async ensureRootNodeId(): Promise<string | null> {
		if (this.rootNodeId) {
			return this.rootNodeId
		}

		try {
			const meRes = await this.client.getMe()
			if (meRes.success && meRes.data?.storage?.root_node_id) {
				const rootId = String(meRes.data.storage.root_node_id)
				this.setRootNodeId(rootId)
				return rootId
			}
		} catch {
			// fallback
		}

		return null
	}

	async list(path: string, _opts?: FileBrowserListOptions): Promise<FileBrowserListResult<Record<string, unknown>>> {
		const normalized = normalizeFileBrowserPath(path)
		await this.ensureRootNodeId()

		let parentId: string | undefined

		if (normalized === ROOT_PATH) {
			parentId = this.rootNodeId ?? undefined
		} else {
			parentId = this.pathToId.get(normalized)
			if (!parentId) {
				throw new FileBrowserAdapterError('not_found', `Không tìm thấy thư mục: ${normalized}`)
			}
		}

		const queryParams: Record<string, string | number | undefined> = {
			page: 0,
			size: 200,
			sort: 'name,asc'
		}

		if (parentId) {
			queryParams.parent_id = parentId
		}

		const response = await this.client.get<PagedResponse<NodeResponse>>(
			'/api/v1/storage-service/nodes',
			queryParams
		)

		if (!response.success || !response.data) {
			throw new FileBrowserAdapterError('not_found', response.message || 'Không thể tải danh sách tệp.')
		}

		const items: FileNode<Record<string, unknown>>[] = []

		for (const node of response.data.content) {
			const childPath = joinFileBrowserPath(normalized, node.name)
			const nodeId = String(node.id)

			this.pathToId.set(childPath, nodeId)
			this.idToPath.set(nodeId, childPath)

			items.push({
				id: nodeId,
				name: node.name,
				path: childPath,
				kind: node.is_folder ? 'folder' : 'file',
				size: node.size_bytes ?? undefined,
				mimeType: node.mime_type ?? undefined,
				modifiedAt: node.updated_at || node.created_at,
				etag: node.etag ?? undefined,
				metadata: {
					nodeStatus: node.node_status,
					isStarred: node.is_starred,
					fileHash: node.file_hash
				}
			})
		}

		return { items }
	}

	async createFolder(path: string): Promise<FileNode<Record<string, unknown>>> {
		const normalized = normalizeFileBrowserPath(path)
		const parentPath = getFileBrowserDirname(normalized)
		const folderName = getFileBrowserBasename(normalized)

		await this.ensureRootNodeId()
		const parentId = parentPath === ROOT_PATH ? this.rootNodeId : this.pathToId.get(parentPath)

		const res = await this.client.post<NodeResponse>('/api/v1/storage-service/nodes/folders', {
			name: folderName,
			parent_id: parentId ? String(parentId) : null
		})

		if (!res.success || !res.data) {
			throw new FileBrowserAdapterError('conflict', res.message || 'Không thể tạo thư mục mới.')
		}

		const node = res.data
		const nodeId = String(node.id)
		this.pathToId.set(normalized, nodeId)
		this.idToPath.set(nodeId, normalized)

		return {
			id: nodeId,
			name: node.name,
			path: normalized,
			kind: 'folder',
			modifiedAt: node.updated_at || node.created_at,
			metadata: {
				nodeStatus: node.node_status,
				isStarred: node.is_starred
			}
		}
	}

	async rename(path: string, newName: string): Promise<FileNode<Record<string, unknown>>> {
		const normalized = normalizeFileBrowserPath(path)
		const nodeId = this.pathToId.get(normalized)
		if (!nodeId) {
			throw new FileBrowserAdapterError('not_found', `Không tìm thấy phần tử để đổi tên: ${normalized}`)
		}

		const res = await this.client.patch<NodeResponse>(`/api/v1/storage-service/nodes/${nodeId}`, {
			name: newName
		})

		if (!res.success || !res.data) {
			throw new FileBrowserAdapterError('conflict', res.message || 'Đổi tên thất bại.')
		}

		const parentPath = getFileBrowserDirname(normalized)
		const newPath = joinFileBrowserPath(parentPath, newName)

		this.pathToId.delete(normalized)
		this.pathToId.set(newPath, nodeId)
		this.idToPath.set(nodeId, newPath)

		const node = res.data
		return {
			id: nodeId,
			name: node.name,
			path: newPath,
			kind: node.is_folder ? 'folder' : 'file',
			size: node.size_bytes ?? undefined,
			mimeType: node.mime_type ?? undefined,
			modifiedAt: node.updated_at || node.created_at,
			metadata: {
				nodeStatus: node.node_status,
				isStarred: node.is_starred
			}
		}
	}

	async move(from: string[], toDir: string): Promise<void> {
		const targetPath = normalizeFileBrowserPath(toDir)
		await this.ensureRootNodeId()

		const targetParentId = targetPath === ROOT_PATH ? this.rootNodeId : this.pathToId.get(targetPath)
		if (!targetParentId) {
			throw new FileBrowserAdapterError('not_found', `Không tìm thấy thư mục đích: ${targetPath}`)
		}

		const nodeIds: string[] = []
		for (const p of from) {
			const id = this.pathToId.get(normalizeFileBrowserPath(p))
			if (id) {
				nodeIds.push(id)
			}
		}

		if (nodeIds.length === 0) return

		const res = await this.client.post('/api/v1/storage-service/nodes/move', {
			target_parent_id: String(targetParentId),
			node_ids: nodeIds
		})

		if (!res.success) {
			throw new FileBrowserAdapterError('conflict', res.message || 'Di chuyển phần tử thất bại.')
		}

		// Update path cache
		for (const p of from) {
			const norm = normalizeFileBrowserPath(p)
			const id = this.pathToId.get(norm)
			if (id) {
				const baseName = getFileBrowserBasename(norm)
				const nextPath = joinFileBrowserPath(targetPath, baseName)
				this.pathToId.delete(norm)
				this.pathToId.set(nextPath, id)
				this.idToPath.set(id, nextPath)
			}
		}
	}

	async delete(paths: string[]): Promise<void> {
		const nodeIds: string[] = []
		for (const p of paths) {
			const norm = normalizeFileBrowserPath(p)
			const id = this.pathToId.get(norm)
			if (id) {
				nodeIds.push(id)
			}
		}

		if (nodeIds.length === 0) return

		const res = await this.client.delete('/api/v1/storage-service/nodes', {
			node_ids: nodeIds.join(',')
		})

		if (!res.success) {
			throw new FileBrowserAdapterError('conflict', res.message || 'Xóa tệp/thư mục thất bại.')
		}

		for (const p of paths) {
			const norm = normalizeFileBrowserPath(p)
			const id = this.pathToId.get(norm)
			if (id) {
				this.pathToId.delete(norm)
				this.idToPath.delete(id)
			}
		}
	}

	async signedUrl(path: string): Promise<string> {
		const normalized = normalizeFileBrowserPath(path)
		const nodeId = this.pathToId.get(normalized)
		if (!nodeId) {
			throw new FileBrowserAdapterError('not_found', `Không tìm thấy ID của tệp: ${normalized}`)
		}

		const res = await this.client.post<DownloadUrlsResponse>('/api/v1/storage-service/files/download-urls', {
			files: [{ node_id: nodeId }]
		})

		if (!res.success || !res.data?.download_urls?.length) {
			throw new FileBrowserAdapterError('access_denied', res.message || 'Không thể tạo URL tải xuống.')
		}

		return res.data.download_urls[0].url
	}

	async upload(path: string, file: File, opts?: FileBrowserUploadOptions): Promise<FileNode<Record<string, unknown>>> {
		const normalized = normalizeFileBrowserPath(path)
		const parentPath = getFileBrowserDirname(normalized)
		const fileName = file.name || getFileBrowserBasename(normalized)

		await this.ensureRootNodeId()
		const parentId = parentPath === ROOT_PATH ? this.rootNodeId : this.pathToId.get(parentPath)

		// 1. Calculate file SHA-256 hash
		const hash = await calculateSha256(file)

		// 2. Request Presigned Upload URL
		const res = await this.client.post<UploadUrlsResponse>('/api/v1/storage-service/files/upload-urls', {
			files: [
				{
					parent_id: parentId ? String(parentId) : null,
					name: fileName,
					size: file.size,
					mime_type: file.type || 'application/octet-stream',
					hash
				}
			]
		})

		if (!res.success || !res.data?.presigned_urls?.length) {
			throw new FileBrowserAdapterError('conflict', res.message || 'Không thể yêu cầu URL tải lên.')
		}

		const presigned = res.data.presigned_urls[0]
		const nodeId = String(presigned.node_id)
		this.pathToId.set(normalized, nodeId)
		this.idToPath.set(nodeId, normalized)

		// 3. Instant upload check (deduplication)
		if (presigned.instant_uploaded) {
			opts?.onProgress?.(file.size, file.size)
			return {
				id: nodeId,
				name: fileName,
				path: normalized,
				kind: 'file',
				size: file.size,
				mimeType: file.type || 'application/octet-stream',
				modifiedAt: new Date().toISOString(),
				metadata: {
					nodeStatus: 'ACTIVE',
					instantUploaded: true
				}
			}
		}

		// 4. Direct PUT upload to MinIO with progress
		await new Promise<void>((resolve, reject) => {
			const xhr = new XMLHttpRequest()
			xhr.open('PUT', presigned.url)
			xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')

			if (opts?.signal) {
				opts.signal.addEventListener('abort', () => xhr.abort())
			}

			xhr.upload.onprogress = (evt) => {
				if (evt.lengthComputable && opts?.onProgress) {
					opts.onProgress(evt.loaded, evt.total)
				}
			}

			xhr.onload = () => {
				if (xhr.status >= 200 && xhr.status < 300) {
					resolve()
				} else {
					reject(new FileBrowserAdapterError('access_denied', `MinIO upload failed with status ${xhr.status}`))
				}
			}

			xhr.onerror = () => {
				reject(new FileBrowserAdapterError('access_denied', 'Lỗi mạng khi tải lên MinIO.'))
			}

			xhr.onabort = () => {
				reject(new FileBrowserAdapterError('aborted', 'Tải lên bị hủy.'))
			}

			xhr.send(file)
		})

		return {
			id: nodeId,
			name: fileName,
			path: normalized,
			kind: 'file',
			size: file.size,
			mimeType: file.type || 'application/octet-stream',
			modifiedAt: new Date().toISOString(),
			metadata: {
				nodeStatus: 'ACTIVE'
			}
		}
	}

	async stat(path: string): Promise<FileNode<Record<string, unknown>>> {
		const normalized = normalizeFileBrowserPath(path)
		const nodeId = this.pathToId.get(normalized)
		if (!nodeId) {
			throw new FileBrowserAdapterError('not_found', `Không tìm thấy node: ${normalized}`)
		}

		const res = await this.client.get<NodeResponse>(`/api/v1/storage-service/nodes/${nodeId}`)
		if (!res.success || !res.data) {
			throw new FileBrowserAdapterError('not_found', res.message || 'Không tìm thấy thông tin tệp.')
		}

		const node = res.data
		return {
			id: String(node.id),
			name: node.name,
			path: normalized,
			kind: node.is_folder ? 'folder' : 'file',
			size: node.size_bytes ?? undefined,
			mimeType: node.mime_type ?? undefined,
			modifiedAt: node.updated_at || node.created_at,
			metadata: {
				nodeStatus: node.node_status,
				isStarred: node.is_starred
			}
		}
	}
}
