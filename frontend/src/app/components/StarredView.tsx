import React, { useEffect, useState, useCallback } from 'react'
import { Star, Download, RefreshCw } from 'lucide-react'
import { apiClient } from '../../adapters/agentic-cloud/api-client'
import { useNotification } from '../context/NotificationContext'
import type { NodeResponse } from '../../adapters/agentic-cloud/types'

function formatBytes(bytes?: number | null): string {
	if (!bytes || bytes <= 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(1024))
	return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export const StarredView: React.FC<{
	onNavigateFolder?: (folderPath: string) => void
}> = ({ onNavigateFolder }) => {
	const { showToast } = useNotification()
	const [items, setItems] = useState<NodeResponse[]>([])
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState<string | null>(null)
	const [actionId, setActionId] = useState<string | number | null>(null)

	const loadStarred = useCallback(async () => {
		setLoading(true)
		setError(null)
		try {
			const res = await apiClient.getStarredNodes({ page: 0, size: 200, sort: 'updated_at,desc' })
			if (res.success && res.data) {
				setItems(res.data.content || [])
			} else {
				throw new Error(res.message || 'Không thể tải danh sách gắn sao')
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Lỗi tải danh sách gắn sao')
		} finally {
			setLoading(false)
		}
	}, [])

	useEffect(() => {
		loadStarred()
	}, [loadStarred])

	const handleToggleStar = async (node: NodeResponse) => {
		setActionId(node.id)
		try {
			const res = await apiClient.toggleStar(node.id, false)
			if (res.success) {
				setItems((prev) => prev.filter((item) => item.id !== node.id))
				showToast({
					type: 'success',
					message: `Đã hủy gắn sao "${node.name}"`
				})
			} else {
				throw new Error(res.message || 'Hủy gắn sao thất bại')
			}
		} catch (err: unknown) {
			showToast({
				type: 'error',
				message: err instanceof Error ? err.message : 'Thao tác thất bại'
			})
		} finally {
			setActionId(null)
		}
	}

	const handleDownloadFile = async (node: NodeResponse) => {
		try {
			showToast({
				type: 'info',
				message: `Bắt đầu tải về "${node.name}"...`
			})
			const res = await apiClient.post<import('../../adapters/agentic-cloud/types').DownloadUrlsResponse>(
				'/api/v1/storage-service/files/download-urls',
				{ files: [{ node_id: String(node.id) }] },
				{
					'Content-Disposition': 'attachment',
					'X-Download': 'true'
				}
			)
			if (res.success && res.data?.download_urls?.length) {
				const downloadUrl = res.data.download_urls[0].url
				const a = document.createElement('a')
				a.href = downloadUrl
				a.download = node.name
				a.target = '_blank'
				document.body.appendChild(a)
				a.click()
				a.remove()
				showToast({
					type: 'success',
					message: `Tải xuống hoàn tất: ${node.name}`
				})
			}
		} catch (err: unknown) {
			showToast({
				type: 'error',
				message: err instanceof Error ? err.message : 'Tải xuống thất bại'
			})
		}
	}

	return (
		<div className="flex h-full w-full flex-col min-h-0 overflow-hidden bg-white p-6 dark:bg-neutral-900">
			{/* Top Bar */}
			<div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 pb-4 dark:border-neutral-800">
				<div>
					<h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white flex items-center gap-2">
						<span className="text-2xl text-amber-500">⭐</span>
						<span>Đã gắn sao</span>
					</h2>
					<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">
						Truy cập nhanh các tệp và thư mục quan trọng mà bạn đã đánh dấu sao
					</p>
				</div>

				<button
					type="button"
					onClick={loadStarred}
					disabled={loading}
					className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 bg-white px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-300 transition"
				>
					<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
					<span>Làm mới</span>
				</button>
			</div>

			{error && (
				<div className="mb-4 rounded-xl bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
					{error}
				</div>
			)}

			{/* List Grid */}
			<div className="flex-1 min-h-0 overflow-y-auto">
				{loading ? (
					<div className="flex h-48 items-center justify-center text-xs text-gray-400">
						Đang tải danh sách gắn sao...
					</div>
				) : items.length === 0 ? (
					<div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 text-center dark:border-neutral-800">
						<span className="text-4xl">⭐</span>
						<p className="mt-2 text-sm font-semibold text-gray-700 dark:text-neutral-300">
							Chưa có mục nào được gắn sao
						</p>
						<p className="text-xs text-gray-400">
							Bấm vào biểu tượng ngôi sao bên cạnh bất kỳ file nào để lưu nhanh vào danh sách này.
						</p>
					</div>
				) : (
					<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
						{items.map((node) => (
							<div
								key={node.id}
								className="flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-4 shadow-sm transition hover:shadow-md dark:border-neutral-800 dark:bg-neutral-800/60"
							>
								<div>
									<div className="flex items-start justify-between gap-2">
										<div className="flex items-center gap-2.5 min-w-0">
											<span className="text-2xl">{node.is_folder ? '📁' : '📄'}</span>
											<div className="min-w-0">
												<h3
													className={`truncate text-sm font-bold text-gray-900 dark:text-white ${
														node.is_folder
															? 'cursor-pointer hover:text-indigo-600 dark:hover:text-indigo-400'
															: ''
													}`}
													onClick={() => {
														if (node.is_folder && onNavigateFolder) {
															onNavigateFolder(`/${node.name}`)
														}
													}}
													title={node.name}
												>
													{node.name}
												</h3>
												<p className="mt-0.5 text-[11px] text-gray-400">
													{node.is_folder ? 'Thư mục' : formatBytes(node.size_bytes)}
												</p>
											</div>
										</div>

										<button
											type="button"
											onClick={() => handleToggleStar(node)}
											disabled={actionId === node.id}
											className="rounded-lg p-1 text-amber-500 hover:bg-amber-50 dark:hover:bg-neutral-700 transition"
											title="Hủy gắn sao"
										>
											<Star size={18} fill="currentColor" />
										</button>
									</div>
								</div>

								<div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-neutral-700/60">
									<span className="text-[11px] text-gray-400">
										{node.updated_at
											? new Date(node.updated_at).toLocaleDateString('vi-VN')
											: '--'}
									</span>

									{!node.is_folder && (
										<button
											type="button"
											onClick={() => handleDownloadFile(node)}
											className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-2.5 py-1 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:text-neutral-300 dark:hover:bg-neutral-700 transition"
										>
											<Download size={13} />
											<span>Tải về</span>
										</button>
									)}
								</div>
							</div>
						))}
					</div>
				)}
			</div>
		</div>
	)
}
