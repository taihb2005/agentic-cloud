import React, { useEffect, useState, useCallback } from 'react'
import { RotateCcw, Trash2, RefreshCw, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../adapters/agentic-cloud/api-client'
import { useAuth } from '../context/AuthContext'
import { useNotification } from '../context/NotificationContext'
import type { NodeResponse } from '../../adapters/agentic-cloud/types'

function formatBytes(bytes?: number | null): string {
	if (!bytes || bytes <= 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(1024))
	return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export const TrashView: React.FC = () => {
	const { refreshQuota } = useAuth()
	const { showToast, confirm } = useNotification()
	const [items, setItems] = useState<NodeResponse[]>([])
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState<string | null>(null)
	const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
	const [actionLoading, setActionLoading] = useState(false)
	const [confirmEmpty, setConfirmEmpty] = useState(false)

	const loadTrash = useCallback(async () => {
		setLoading(true)
		setError(null)
		try {
			const res = await apiClient.getTrash({ page: 0, size: 200, sort: 'deleted_at,desc' })
			if (res.success && res.data) {
				setItems(res.data.content || [])
			} else {
				throw new Error(res.message || 'Không thể tải danh sách thùng rác')
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Lỗi tải thùng rác')
		} finally {
			setLoading(false)
		}
	}, [])

	useEffect(() => {
		loadTrash()
	}, [loadTrash])

	const toggleSelect = (id: string | number) => {
		const strId = String(id)
		const next = new Set(selectedIds)
		if (next.has(strId)) {
			next.delete(strId)
		} else {
			next.add(strId)
		}
		setSelectedIds(next)
	}

	const toggleSelectAll = () => {
		if (selectedIds.size === items.length) {
			setSelectedIds(new Set())
		} else {
			setSelectedIds(new Set(items.map((i) => String(i.id))))
		}
	}

	// Khôi phục (Restore)
	const handleRestore = async (nodeIds: string[]) => {
		if (nodeIds.length === 0) return
		setActionLoading(true)
		try {
			const res = await apiClient.restoreTrash(nodeIds)
			if (res.success) {
				setSelectedIds(new Set())
				await loadTrash()
				await refreshQuota()
				showToast({
					type: 'success',
					message: `Đã khôi phục thành công ${nodeIds.length} mục!`
				})
			} else {
				throw new Error(res.message || 'Khôi phục thất bại')
			}
		} catch (err: unknown) {
			showToast({
				type: 'error',
				message: err instanceof Error ? err.message : 'Khôi phục thất bại'
			})
		} finally {
			setActionLoading(false)
		}
	}

	// Xóa vĩnh viễn (Permanent Delete)
	const handlePermanentDelete = async (nodeIds: string[]) => {
		const isAll = nodeIds.length === 0
		const confirmed = await confirm({
			title: isAll ? 'Dọn sạch thùng rác' : 'Xóa vĩnh viễn',
			message: isAll
				? 'Bạn có chắc chắn muốn dọn sạch thùng rác? Toàn bộ file sẽ bị xóa vĩnh viễn và không thể khôi phục!'
				: `Bạn có chắc chắn muốn xóa vĩnh viễn ${nodeIds.length} mục đã chọn? Thao tác này không thể hoàn tác.`,
			type: 'danger',
			confirmText: isAll ? 'Dọn sạch thùng rác' : 'Xóa vĩnh viễn',
			cancelText: 'Hủy'
		})
		if (!confirmed) return

		setActionLoading(true)
		try {
			const res = await apiClient.permanentDeleteTrash(nodeIds)
			if (res.success) {
				setSelectedIds(new Set())
				setConfirmEmpty(false)
				await loadTrash()
				await refreshQuota()
				showToast({
					type: 'success',
					message: isAll ? 'Đã dọn sạch thùng rác!' : `Đã xóa vĩnh viễn ${nodeIds.length} mục!`
				})
			} else {
				throw new Error(res.message || 'Xóa vĩnh viễn thất bại')
			}
		} catch (err: unknown) {
			showToast({
				type: 'error',
				message: err instanceof Error ? err.message : 'Xóa vĩnh viễn thất bại'
			})
		} finally {
			setActionLoading(false)
		}
	}

	return (
		<div className="flex h-full w-full flex-col min-h-0 overflow-hidden bg-white p-6 dark:bg-neutral-900">
			{/* Top Bar */}
			<div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 pb-4 dark:border-neutral-800">
				<div>
					<h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white flex items-center gap-2">
						<span className="text-2xl">🗑️</span>
						<span>Thùng rác</span>
					</h2>
					<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">
						Các mục trong thùng rác có thể được khôi phục hoặc xóa vĩnh viễn để giải phóng dung lượng
					</p>
				</div>

				<div className="flex flex-wrap items-center gap-2">
					<button
						type="button"
						onClick={loadTrash}
						disabled={loading || actionLoading}
						className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 bg-white px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-300 transition"
					>
						<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
						<span>Làm mới</span>
					</button>

					{selectedIds.size > 0 && (
						<>
							<button
								type="button"
								onClick={() => handleRestore(Array.from(selectedIds))}
								disabled={actionLoading}
								className="inline-flex items-center gap-1.5 rounded-xl bg-indigo-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:opacity-50"
							>
								<RotateCcw size={14} />
								<span>Khôi phục ({selectedIds.size})</span>
							</button>

							<button
								type="button"
								onClick={() => handlePermanentDelete(Array.from(selectedIds))}
								disabled={actionLoading}
								className="inline-flex items-center gap-1.5 rounded-xl bg-rose-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-rose-700 disabled:opacity-50"
							>
								<Trash2 size={14} />
								<span>Xóa vĩnh viễn ({selectedIds.size})</span>
							</button>
						</>
					)}

					{items.length > 0 && (
						<button
							type="button"
							onClick={() => handlePermanentDelete([])}
							disabled={actionLoading}
							className="inline-flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300 transition"
						>
							<AlertTriangle size={14} />
							<span>Dọn sạch thùng rác</span>
						</button>
					)}
				</div>
			</div>

			{error && (
				<div className="mb-4 rounded-xl bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
					{error}
				</div>
			)}

			{/* Main Table / List */}
			<div className="flex-1 min-h-0 overflow-y-auto">
				{loading ? (
					<div className="flex h-48 items-center justify-center text-xs text-gray-400">
						Đang tải dữ liệu thùng rác...
					</div>
				) : items.length === 0 ? (
					<div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 text-center dark:border-neutral-800">
						<span className="text-4xl">✨</span>
						<p className="mt-2 text-sm font-semibold text-gray-700 dark:text-neutral-300">
							Thùng rác trống
						</p>
						<p className="text-xs text-gray-400">
							Khi bạn xóa file hoặc thư mục trong bộ nhớ, chúng sẽ xuất hiện ở đây.
						</p>
					</div>
				) : (
					<div className="w-full overflow-x-auto">
						<table className="w-full text-left border-collapse">
							<thead>
								<tr className="border-b border-gray-200 text-xs font-semibold text-gray-500 dark:border-neutral-800 dark:text-neutral-400">
									<th className="py-2.5 px-3 w-10">
										<input
											type="checkbox"
											checked={items.length > 0 && selectedIds.size === items.length}
											onChange={toggleSelectAll}
											className="rounded text-indigo-600 cursor-pointer"
										/>
									</th>
									<th className="py-2.5 px-3">Tên tệp / thư mục</th>
									<th className="py-2.5 px-3">Kích thước</th>
									<th className="py-2.5 px-3">Thời gian xóa</th>
									<th className="py-2.5 px-3 text-right">Thao tác</th>
								</tr>
							</thead>
							<tbody className="divide-y divide-gray-100 text-xs dark:divide-neutral-800">
								{items.map((node) => {
									const isChecked = selectedIds.has(String(node.id))
									return (
										<tr
											key={node.id}
											className={`transition hover:bg-gray-50 dark:hover:bg-neutral-800/50 ${
												isChecked ? 'bg-indigo-50/50 dark:bg-indigo-950/20' : ''
											}`}
										>
											<td className="py-3 px-3">
												<input
													type="checkbox"
													checked={isChecked}
													onChange={() => toggleSelect(node.id)}
													className="rounded text-indigo-600 cursor-pointer"
												/>
											</td>
											<td className="py-3 px-3 font-medium text-gray-900 dark:text-white">
												<div className="flex items-center gap-2.5">
													<span className="text-xl">{node.is_folder ? '📁' : '📄'}</span>
													<span className="truncate max-w-xs">{node.name}</span>
												</div>
											</td>
											<td className="py-3 px-3 text-gray-500 dark:text-neutral-400">
												{node.is_folder ? '--' : formatBytes(node.size_bytes)}
											</td>
											<td className="py-3 px-3 text-gray-500 dark:text-neutral-400">
												{node.deleted_at
													? new Date(node.deleted_at).toLocaleString('vi-VN')
													: '--'}
											</td>
											<td className="py-3 px-3 text-right">
												<div className="inline-flex items-center gap-1.5">
													<button
														type="button"
														onClick={() => handleRestore([String(node.id)])}
														disabled={actionLoading}
														className="rounded-lg p-1.5 text-gray-500 hover:bg-indigo-50 hover:text-indigo-600 dark:text-neutral-400 dark:hover:bg-indigo-950/60 dark:hover:text-indigo-400 transition"
														title="Khôi phục mục này"
													>
														<RotateCcw size={15} />
													</button>
													<button
														type="button"
														onClick={() => handlePermanentDelete([String(node.id)])}
														disabled={actionLoading}
														className="rounded-lg p-1.5 text-gray-500 hover:bg-rose-50 hover:text-rose-600 dark:text-neutral-400 dark:hover:bg-rose-950/60 dark:hover:text-rose-400 transition"
														title="Xóa vĩnh viễn mục này"
													>
														<Trash2 size={15} />
													</button>
												</div>
											</td>
										</tr>
									)
								})}
							</tbody>
						</table>
					</div>
				)}
			</div>
		</div>
	)
}
